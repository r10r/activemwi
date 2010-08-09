package de.codewheel.activemwi;

import java.io.IOException;

import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.MailboxStatusAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.action.StatusAction;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.event.PeerStatusEvent;
import org.asteriskjava.manager.response.MailboxStatusResponse;
import org.asteriskjava.manager.response.ManagerResponse;

public class CopyOfActiveMWI implements ManagerEventListener {

	private ManagerConnection eventConnection;
	private ManagerConnection cmdConnection;

	public CopyOfActiveMWI() throws IOException {
		ManagerConnectionFactory factory = new ManagerConnectionFactory("172.16.123.222", "manager", "tekO9BNfS8J668TkZZLI7Z");
		
		// create connections
		eventConnection = factory.createManagerConnection();
		cmdConnection = factory.createManagerConnection();
	}

	public void run() throws IOException, AuthenticationFailedException,
			TimeoutException, InterruptedException {

		// register for events
		eventConnection.addEventListener(this);
		
		while(true) {
	
		// connect to Asterisk and log in
		eventConnection.login();

		// request channel state
		eventConnection.sendAction(new StatusAction());

		// wait for events to come in
		while(eventConnection.getState()==ManagerConnectionState.CONNECTED) {
			Thread.sleep(60000L);
		}

		// and finally log off and disconnect
		eventConnection.logoff();
		}
	}

	public static void main(String[] args) throws Exception {
		CopyOfActiveMWI helloEvents;

		helloEvents = new CopyOfActiveMWI();
		helloEvents.run();
	}

	public void onManagerEvent(ManagerEvent event) {

		if (event.getClass() == PeerStatusEvent.class) {
			onPeerStatusEvent((PeerStatusEvent) event);
		}

	}

	public void onPeerStatusEvent(PeerStatusEvent event) {

		if (event.getPeerStatus().equals(PeerStatusEvent.STATUS_REACHABLE)) {
				
			try {
				// open command connection
				cmdConnection.login();
				
				// query mailbox for new messages
				String peer = event.getPeer();
				System.out.println(peer + " is now reachable.");
				MailboxStatusAction mboxAction = new MailboxStatusAction(peer.replace("/SIP", "") + "@default");
				MailboxStatusResponse mboxResponse = (MailboxStatusResponse) cmdConnection.sendAction(mboxAction, 10000L);
				
				// connect reachable client to mailbox
				if (mboxResponse.getWaiting())
				{
					System.out.println(peer + " has new messages.");
					connectToMailbox(peer);
				} else {
					System.out.println(peer + " has no new messages.");
				}
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticationFailedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				cmdConnection.logoff();
			}
			
		}
	}

	public void connectToMailbox(String peer) {
		try {
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			OriginateAction mboxConnect;
			ManagerResponse originateResponse;
			mboxConnect = new OriginateAction();
			mboxConnect.setChannel(peer);
			mboxConnect.setContext("outgoing");
			mboxConnect.setExten("9000");
			mboxConnect.setCallerId(peer.replace("SIP/", ""));
			mboxConnect.setPriority(new Integer(1));
			mboxConnect.setTimeout(30000L);

			// send the originate action and wait for a maximum of 30 seconds
			// for Asterisk
			// to send a reply
			System.out.println("Trying to connect " + peer + " to his mailbox.");
			originateResponse = cmdConnection.sendAction(mboxConnect,
					30000L);

			// print out whether the originate succeeded or not
			System.out.println("Response to mailbox connection was: " + originateResponse.getResponse());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
