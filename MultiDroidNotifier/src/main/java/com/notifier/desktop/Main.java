/*
 * Android Notifier Desktop is a multiplatform remote notification client for Android devices.
 *
 * Copyright (C) 2010  Leandro Aparecido
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.notifier.desktop;

import java.io.*;
import java.nio.channels.*;
import java.util.*;

import org.apache.commons.cli.*;
import org.slf4j.*;

import com.google.common.io.*;
import com.google.inject.*;
import com.notifier.desktop.os.*;
import com.notifier.desktop.service.*;
import com.notifier.desktop.service.impl.*;
import com.notifier.desktop.view.*;
import com.notifier.desktop.view.impl.*;

import static java.util.concurrent.TimeUnit.*;

public class Main {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	private static final String NO_TRAY_SHORT = "t";
	private static final String NO_TRAY_LONG = "no-tray";

	private static final String SHOW_PREFERENCES_SHORT = "p";
	private static final String SHOW_PREFERENCES_LONG = "show-preferences";

	private static final String IS_RUNNING_SHORT = "i";
	private static final String IS_RUNNING_LONG = "is-running";

	private static final String STOP_SHORT = "s";
	private static final String STOP_LONG = "stop";

	private static final String HELP_SHORT = "h";
	private static final String HELP_LONG = "help";

	public static void main(String[] args) {
		Options options = createCommandLineOptions();
		try {
			CommandLineParser commandLineParser = new GnuParser();
			CommandLine line = commandLineParser.parse(options, args);

			if (line.getOptions().length > 1) {
				showMessage("Only one parameter may be specified");
			}
			if (line.getArgs().length > 0) {
				showMessage("Non-recognized parameters: " + Arrays.toString(line.getArgs()));
			}
			if (line.hasOption(HELP_SHORT)) {
				printHelp(options);
				return;
			}
			if (line.hasOption(IS_RUNNING_SHORT)) {
				ServiceClient client = new ServiceClientImpl();
				if (client.isRunning()) {
					showMessage(Application.NAME + " is running");
				} else {
					showMessage(Application.NAME + " is not running");
				}
				return;
			}
			if (line.hasOption(STOP_SHORT)) {
				ServiceClient client = new ServiceClientImpl();
				if (client.stop()) {
					showMessage("Sent stop signal to " + Application.NAME + " successfully");
				} else {
					showMessage(Application.NAME + " is not running or an error occurred, see log for details");
				}
				return;
			}

			boolean trayIcon = !line.hasOption(NO_TRAY_SHORT);
			boolean showPreferences = line.hasOption(SHOW_PREFERENCES_SHORT);

			if (!getExclusiveExecutionLock()) {
				showMessage("There can be only one instance of " + Application.NAME + " running at a time");
				return;
			}
			Injector injector = Guice.createInjector(Stage.PRODUCTION, new ApplicationModule());
			Application application = injector.getInstance(Application.class);
			application.start(trayIcon, showPreferences);
		} catch (Throwable t) {
			System.out.println(t.getMessage());
			logger.error("Error starting", t);
		}
	}

	private static Options createCommandLineOptions() {
		Options options = new Options();
		options.addOption(NO_TRAY_SHORT, NO_TRAY_LONG, false, "don't show tray icon (System default notification display will not be shown)");
		options.addOption(SHOW_PREFERENCES_SHORT, SHOW_PREFERENCES_LONG, false, "show preferences window immediately");
		options.addOption(IS_RUNNING_SHORT, IS_RUNNING_LONG, false, "show running status");
		options.addOption(STOP_SHORT, STOP_LONG, false, "stop " + Application.NAME + " if it's running");
		options.addOption(HELP_SHORT, HELP_LONG, false, "show help information");
		return options;
	}

	private static void printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		String cmdSyntax = "android-notifier-desktop";
		if (OperatingSystems.CURRENT_FAMILY == OperatingSystems.Family.WINDOWS) {
			StringWriter s = new StringWriter();
			formatter.printHelp(new PrintWriter(s), 150, cmdSyntax, null, options, formatter.getLeftPadding(), formatter.getDescPadding(), null, true);
			showMessage(s.toString());
		} else {
			formatter.printHelp(cmdSyntax, options, true);
		}
	}

	private static boolean getExclusiveExecutionLock() throws IOException {
		File lockFile = new File(OperatingSystems.getWorkDirectory(), Application.ARTIFACT_ID + ".lock");
		Files.createParentDirs(lockFile);
		lockFile.createNewFile();
		final RandomAccessFile randomAccessFile = new RandomAccessFile(lockFile, "rw");
		final FileChannel fileChannel = randomAccessFile.getChannel();
		final FileLock fileLock = fileChannel.tryLock();
		if (fileLock == null) {
			Closeables.closeQuietly(fileChannel);
			Closeables.closeQuietly(randomAccessFile);
			return false;
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					fileLock.release();
				} catch (IOException e) {
					System.err.println("Error releasing file lock");
					e.printStackTrace(System.err);
				} finally {
					Closeables.closeQuietly(fileChannel);
					Closeables.closeQuietly(randomAccessFile);
				}
			}
		});
		return true;
	}

	private static void showMessage(final String msg) {
		if (OperatingSystems.CURRENT_FAMILY == OperatingSystems.Family.WINDOWS) {
			// Launch4j does not send output to stdout
			final SwtManager swtManager = new SwtManagerImpl();
			try {
				swtManager.start();
				new Thread(new Runnable() {
					@Override
					public void run() {
						Dialogs.showInfo(swtManager, Application.NAME, msg, true);
						try {
							SECONDS.sleep(5);
						} catch (InterruptedException e) {
							// Do nothing
						}
						swtManager.stop();
					}
				}).start();
				swtManager.runEventLoop();
			} catch (Throwable t) {
				// No need to handle this
			} finally {
				logger.info(msg);
			}
		} else {
			System.out.println(msg);
		}
	}
}
