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
package com.notifier.desktop.notification.broadcast.msn;

import java.io.*;
import java.net.*;

import org.slf4j.*;

import net.sf.jml.*;
import net.sf.jml.impl.*;

import com.google.common.base.*;
import com.notifier.desktop.*;

public class MsnNotificationBroadcaster extends RestartableService implements InstantMessagingNotificationBroadcaster {

	private static final Logger logger = LoggerFactory.getLogger(MsnNotificationBroadcaster.class);

	private String username;
	private String password;
	private String targetUsername;

	private MsnMessenger messenger;
	private MsnHandler msnHandler;

	public MsnNotificationBroadcaster() {
		super(false);
		username = "ninguem_faz@hotmail.com";
		password = "testtest";
		//targetUsername = "lehphyro@gmail.com";
		targetUsername = "ninguem_quer@hotmail.com";
	}

	@Override
	public String getName() {
		return "MSN";
	}

	@Override
	protected void doStart() {
		Preconditions.checkNotNull(username, "MSN username must not be null");
		Preconditions.checkNotNull(password, "MSN password must not be null");
		Preconditions.checkNotNull(targetUsername, "MSN target username must not be null");

		logger.info("Logging into msn");
		messenger = MsnMessengerFactory.createMsnMessenger(username, password);
		msnHandler = new MsnHandler(this, messenger, username, targetUsername);

		messenger.setSupportedProtocol(new MsnProtocol[] { MsnProtocol.MSNP15 });
		messenger.getOwner().setInitStatus(MsnUserStatus.ONLINE);
		messenger.getOwner().setInitDisplayName(Application.NAME);
		try {
			MsnObject icon = msnHandler.loadMsnIcon(username, Application.ICON_NAME, MsnObject.TYPE_DISPLAY_PICTURE);
			messenger.getOwner().setInitDisplayPicture(icon);
			messenger.getOwner().setDisplayPicture(icon);
		} catch (IOException e) {
			logger.warn("Could not load avatar icon", e);
		}
		messenger.addListener(msnHandler);
		messenger.login();
	}

	@Override
	public void broadcast(Notification notification, String deviceName, boolean privateMode) {
		if (!isRunning()) {
			return;
		}
		msnHandler.send(notification, deviceName, privateMode);
	}

	@Override
	protected void doStop() throws Exception {
		if (messenger != null) {
			try {
				messenger.logout();
				notifyStopped();
			} catch (Exception e) {
				if (!(e instanceof SocketException)) {
					throw e;
				}
			}
		}
	}

	protected void notifyStarted() { // Make it visible to MsnHandler
		super.notifyStarted();
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setTargetUsername(String targetUsername) {
		this.targetUsername = targetUsername;
	}
}
