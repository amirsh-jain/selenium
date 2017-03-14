package org.openqa.selenium.firefox;


import static org.openqa.selenium.firefox.FirefoxProfile.PORT_PREFERENCE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.internal.ClasspathExtension;
import org.openqa.selenium.firefox.internal.Extension;
import org.openqa.selenium.firefox.internal.FileExtension;
import org.openqa.selenium.remote.service.DriverService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XpiDriverService extends DriverService {

  private final Lock lock = new ReentrantLock();

  private final int port;
  private final FirefoxBinary binary;
  private final FirefoxProfile profile;

  private XpiDriverService(
      File executable,
      int port,
      ImmutableList<String> args,
      ImmutableMap<String, String> environment,
      FirefoxBinary binary,
      FirefoxProfile profile)
      throws IOException {
    super(executable, port, args, environment);

    Preconditions.checkState(port > 0, "Port must be set");

    this.port = port;
    this.binary = binary;
    this.profile = profile;
  }

  @Override
  protected URL getUrl(int port) throws IOException {
    return new URL("http", "localhost", port, "/hub");
  }

  @Override
  public void start() throws IOException {
    lock.lock();
    try {
      profile.setPreference(PORT_PREFERENCE, port);
      addWebDriverExtension(profile);

      File profileDir = profile.layoutOnDisk();
      binary.startProfile(profile, profileDir, "-foreground");

      waitUntilAvailable();
    } finally {
      lock.lock();
    }
  }

  @Override
  public void stop() {
    lock.lock();
    try {
      binary.quit();
    } finally {
      lock.unlock();
    }
  }

  private void addWebDriverExtension(FirefoxProfile profile) {
    if (profile.containsWebDriverExtension()) {
      return;
    }
    profile.addExtension("webdriver", loadCustomExtension().orElse(loadDefaultExtension()));
  }

  private Optional<Extension> loadCustomExtension() {
    String xpiProperty = System.getProperty(FirefoxDriver.SystemProperty.DRIVER_XPI_PROPERTY);
    if (xpiProperty != null) {
      File xpi = new File(xpiProperty);
      return Optional.of(new FileExtension(xpi));
    }
    return Optional.empty();
  }

  private static Extension loadDefaultExtension() {
    return new ClasspathExtension(
        FirefoxProfile.class,
        "/" + FirefoxProfile.class.getPackage().getName().replace(".", "/") + "/webdriver.xpi");
  }

  /**
   * Configures and returns a new {@link XpiDriverService} using the default configuration. In
   * this configuration, the service will use the firefox executable identified by the
   * {@link FirefoxDriver.SystemProperty#BROWSER_BINARY} system property on a free port.
   *
   * @return A new XpiDriverService using the default configuration.
   */
  public static XpiDriverService createDefaultService() {
    try {
      return new XpiDriverService.Builder().usingAnyFreePort().build();
    } catch (WebDriverException e) {
      throw new IllegalStateException(e.getMessage(), e.getCause());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends DriverService.Builder<XpiDriverService, XpiDriverService.Builder> {

    private FirefoxBinary binary = null;
    private FirefoxProfile profile = null;

    private Builder() {
      // Only available through the static factory method in the XpiDriverService
    }

    public Builder withBinary(FirefoxBinary binary) {
      this.binary = Preconditions.checkNotNull(binary);
      return this;
    }

    public Builder withProfile(FirefoxProfile profile) {
      this.profile = Preconditions.checkNotNull(profile);
      return this;
    }

    @Override
    protected File findDefaultExecutable() {
      return new FirefoxBinary().getFile();
    }

    @Override
    protected ImmutableList<String> createArgs() {
      return ImmutableList.of("-foreground");
    }

    @Override
    protected XpiDriverService createDriverService(
        File exe,
        int port,
        ImmutableList<String> args,
        ImmutableMap<String, String> environment) {
      try {
        return new XpiDriverService(
            exe,
            port,
            args,
            environment,
            binary == null ? new FirefoxBinary() : binary,
            profile == null ? new FirefoxProfile() : profile);
      } catch (IOException e) {
        throw new WebDriverException(e);
      }
    }
  }
}
