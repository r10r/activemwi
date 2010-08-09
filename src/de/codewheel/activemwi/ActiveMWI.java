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

public class ActiveMWI implements ManagerEventListener {

	private ManagerConnection eventConnection;
	private ManagerConnection cmdConnection;

	public static String SERVER_IP = "172.16.123.222";
	private static String MANAGER_USER = "manager";
	private static String MANAGER_PASS = "tekO9BNfS8J668TkZZLI7Z";
	private static String MBOX_EXTEN = "9000";
	private static String MBOX_CONTEXT = "outgoing";
	private static long MBOX_RING_TIMEOUT = 20000L;
	private static long MBOX_RETRY_INTERVAL = 600000L;
	private static int MBOX_RETRY_MAX = 6;

	public ActiveMWI() throws IOException {
		ManagerConnectionFactory factory = new ManagerConnectionFactory(
				SERVER_IP, MANAGER_USER, MANAGER_PASS);

		// create connections
		eventConnection = factory.createManagerConnection();
		cmdConnection = factory.createManagerConnection();
	}

	public void run() throws IOException, AuthenticationFailedException,
			TimeoutException, InterruptedException {

		// register for events
		eventConnection.addEventListener(this);

		while (true) {

			// connect to Asterisk and log in
			eventConnection.login();

			// request channel state
			eventConnection.sendAction(new StatusAction());

			// wait for events to come in
			while (eventConnection.getState() == ManagerConnectionState.CONNECTED) {
				Thread.sleep(60000L);
			}

			// and finally log off and disconnect
			eventConnection.logoff();
		}
	}

	public static void main(String[] args) throws Exception {
		ActiveMWI helloEvents;

		helloEvents = new ActiveMWI();
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
				String peer = event.getPeer();
				// connect reachable client to mailbox
				if (hasNewMessages(peer)) {
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

	public boolean hasNewMessages(String peer) {

		try {
			// query mailbox for new messages
			MailboxStatusAction mboxAction = new MailboxStatusAction(peer
					.replace("SIP/", "")
					+ "@default");
			MailboxStatusResponse mboxResponse;
			mboxResponse = (MailboxStatusResponse) cmdConnection.sendAction(
					mboxAction, 10000L);
			// connect reachable client to mailbox
			return mboxResponse.getWaiting();
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
		}
		return false;
	}

	
	// TODO: add better retry policy
	public void connectToMailbox(String peer) {
		try {
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			OriginateAction mboxConnect;
			mboxConnect = new OriginateAction();
			mboxConnect.setChannel(peer);
			mboxConnect.setContext(MBOX_CONTEXT);
			mboxConnect.setExten(MBOX_EXTEN);
			mboxConnect.setCallerId(peer.replace("SIP/", ""));
			mboxConnect.setPriority(new Integer(1));
			mboxConnect.setTimeout(MBOX_RING_TIMEOUT);

			String response = "Error";
			int counter = 0;
			// try to connect the peer to the mailbox until success
			while (!response.equals("Success")) {
				
				counter++;
				
				if (counter >= MBOX_RETRY_MAX ) { 
					System.out.println("Maximum connection retries exceeded. Aborting!");
					break; }
				
				// if client has read message in the meantime don't call him
				if (! hasNewMessages(peer)) {
					System.out.println("Client has read messages in meantime. Aborting!");
					break;
				}
				System.out.println("Trying to connect " + peer
						+ " to his mailbox.");
				response = cmdConnection.sendAction(mboxConnect,
						MBOX_RING_TIMEOUT+5000).getResponse();
				System.out.println("Response to mailbox connection was: "
						+ response);
				
				// if client anwered the call finish calling loop
				if (response.equals("Success")) {
					System.out.println("Client connection was successful.");
					break;
				}
				try {
					Thread.sleep(MBOX_RETRY_INTERVAL);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Connect to mailbox loop finished!");
	}

}
