//Name - Chiam Jack How
//Matric No. - U1722261C
//Lab Group - SSP5
//Seat No. - 6

/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                                       *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

//For Timer functions

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

	/*
	 * ========================================================================* the
	 * following are provided, do not change them!!
	 * ========================================================================
	 */
	// the following are protocol constants.
	public static final int MAX_SEQ = 7;
	public static final int NR_BUFS = (MAX_SEQ + 1) / 2;

	// the following are protocol variables
	private int oldest_frame = 0;
	private PEvent event = new PEvent();
	private Packet out_buf[] = new Packet[NR_BUFS];

	// the following are used for simulation purpose only
	private SWE swe = null;
	private String sid = null;

	// Constructor
	public SWP(SWE sw, String s) {
		swe = sw;
		sid = s;
	}

	// the following methods are all protocol related
	private void init() {
		for (int i = 0; i < NR_BUFS; i++) {
			out_buf[i] = new Packet();
		}
	}

	private void wait_for_event(PEvent e) {
		swe.wait_for_event(e); // may be blocked
		oldest_frame = e.seq; // set timeout frame seq
	}

	private void enable_network_layer(int nr_of_bufs) {
		// network layer is permitted to send if credit is available
		swe.grant_credit(nr_of_bufs);
	}

	private void from_network_layer(Packet p) {
		swe.from_network_layer(p);
	}

	private void to_network_layer(Packet packet) {
		swe.to_network_layer(packet);
	}

	private void to_physical_layer(PFrame fm) {
		System.out.println("SWP: Sending frame: seq = " + fm.seq + " ack = " + fm.ack + " kind = "
				+ PFrame.KIND[fm.kind] + " info = " + fm.info.data);
		System.out.flush();
		swe.to_physical_layer(fm);
	}

	private void from_physical_layer(PFrame fm) {
		PFrame fm1 = swe.from_physical_layer();
		fm.kind = fm1.kind;
		fm.seq = fm1.seq;
		fm.ack = fm1.ack;
		fm.info = fm1.info;
	}

	/*
	 * ===========================================================================*
	 * implement your Protocol Variables and Methods below:
	 * ==========================================================================
	 */

	// "No negative acknowledgement" is set to true
	private static boolean no_nak = true;

	// Checks to see if the frame is within the frame window boundaries
	private static boolean between(int a, int b, int c) {
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
	}
	
	// Increment frame number circularly
		private static int inc(int frame) {
			if (frame < MAX_SEQ) {
				frame = frame + 1;
			} else {
				frame = 0;
			}
			return frame;

		}

	// Construct and send a data,ack, or nak frame
	private void send_frame(int fk, int frame_nr, int frame_expected, Packet buffer[]) {

		PFrame fm = new PFrame(); // scratch variable
		fm.kind = fk; // kind == data,ack or nak
		if (fk == PFrame.DATA)
			fm.info = buffer[frame_nr % NR_BUFS];
		fm.seq = frame_nr;
		fm.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
		if (fk == PFrame.NAK) // Check for no_nak
			no_nak = false;
		to_physical_layer(fm);
		if (fk == PFrame.DATA)
			start_timer(frame_nr);
		stop_ack_timer();
	}

	

	public void protocol6() {

		int ack_expected = 0; // lower edge of sender's window
		int next_frame_to_send = 0; // upper edge of sender's window + 1
		int frame_expected = 0; // lower edge of receiver's window
		int too_far = NR_BUFS; // upper edge of receiver's window + 1
		boolean arrived[] = new boolean[NR_BUFS];
		Packet in_buf[] = new Packet[NR_BUFS];
		PFrame fm = new PFrame();
		init();

		// Initialize the network layer
		enable_network_layer(NR_BUFS);

		// Initialize sliding windows as empty
		for (int i = 0; i < NR_BUFS; i++)
			arrived[i] = false;

		while (true) {
			wait_for_event(event);
			switch (event.type) {

			// Accept,save and transmit a new frame
			case (PEvent.NETWORK_LAYER_READY):
				from_network_layer(out_buf[next_frame_to_send % NR_BUFS]); // Fetch new packet from network layer
				send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf); // Transmit the frame
				next_frame_to_send = inc(next_frame_to_send); // Advance upper window edge
				break;

			// A data or control frame has arrived
			case (PEvent.FRAME_ARRIVAL):
				from_physical_layer(fm); // Fetch incoming frame from physical layer
				if (fm.kind == PFrame.DATA) { // Undamaged frame
					if ((fm.seq != frame_expected) && no_nak) {
						send_frame(PFrame.NAK, 0, frame_expected, out_buf); // Send nak to sender if frame is not
																			// expected

					} else {
						start_ack_timer();
					}

					// Check if frame is within window
					if (between(frame_expected, fm.seq, too_far) && (arrived[fm.seq % NR_BUFS] == false)) {
						arrived[fm.seq % NR_BUFS] = true; // Mark buffer as full
						in_buf[fm.seq % NR_BUFS] = fm.info; // Insert data into buffer

						// Pass frames and advance window
						while (arrived[frame_expected % NR_BUFS]) {
							to_network_layer(in_buf[frame_expected % NR_BUFS]);
							no_nak = true;
							arrived[frame_expected % NR_BUFS] = false; // Remove frame from buffer
							frame_expected = inc(frame_expected); // Advance lower edge of receiver's window
							too_far = inc(too_far); // Advance upper edge of receiver's window
							start_ack_timer(); // To see if a separate ack is needed
						}
					}

				}

				// If nak is within window, request for data for the nak
				if ((fm.kind == PFrame.NAK) && between(ack_expected, (fm.ack + 1) % (MAX_SEQ + 1), next_frame_to_send))
					send_frame(PFrame.DATA, (fm.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf);

				while (between(ack_expected, fm.ack, next_frame_to_send)) {
					stop_timer(ack_expected % NR_BUFS); // Frame arrived intact
					ack_expected = inc(ack_expected); // Advance lower edge of sender's window
					enable_network_layer(1); // Get credit for buffer upon receiving an undamaged frame
				}
				break;

			// Damaged frame
			case (PEvent.CKSUM_ERR):
				if (no_nak) {
					send_frame(PFrame.NAK, 0, frame_expected, out_buf);
				}
				break;

			// Process timed out
			case (PEvent.TIMEOUT):
				send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
				break;

			// Ack timer expired; send ack
			case (PEvent.ACK_TIMEOUT):
				send_frame(PFrame.ACK, oldest_frame, frame_expected, out_buf);
				break;

			default:
				System.out.println("SWP: undefined event type = " + event.type);
				System.out.flush();
			}
		}
	}

	/*
	 * Note: when start_timer() and stop_timer() are called, the "seq" parameter
	 * must be the sequence number, rather than the index of the timer array, of the
	 * frame associated with this timer,
	 */

	private Timer[] timer = new Timer[NR_BUFS]; // Number of timers is equal to number of buffers
	private Timer ack_timer = new Timer(); // Ack timer
	
	// Ack timer is shorter than the retransmission timer
	//so that a correctly received frame is acknowledged  
	//before the  frame's retransmission timer expires and retransmits the frame	
	private static final int ack_delay = 300; 
	private static final int delay = 600;

	private class SWPTimerTask extends TimerTask {
		int seqnr;

		public SWPTimerTask(int seqnr) { // Timer's sequence number
			this.seqnr = seqnr;
		}

		public void run() {
			swe.generate_timeout_event(seqnr); // Generate timeout event
		}
	}

	private class AckTimerTask extends TimerTask {

		public void run() {
			swe.generate_acktimeout_event(); // Generate timeout event
		}
	}

	private void start_timer(int seq) {
		stop_timer(seq); // Stop timer if timer has already started
		timer[seq % NR_BUFS] = new Timer(); // Create new timer for a sequence number
		timer[seq % NR_BUFS].schedule(new SWPTimerTask(seq), delay); // Start new timer that was created
	}

	private void stop_timer(int seq) {
		// Stop timer if it exists
		if (timer[seq % NR_BUFS] != null) {
			timer[seq % NR_BUFS].cancel();
		}
	}

	private void start_ack_timer() {
		stop_ack_timer(); // Stop timer if timer has already started
		ack_timer = new Timer();
		ack_timer.schedule(new AckTimerTask(), ack_delay);
	}

	private void stop_ack_timer() {
		// Stop ack timer if it exists
		if (ack_timer != null) {
			ack_timer.cancel();
		}
	}

}// End of class

/*
 * Note: In class SWE, the following two public methods are available: .
 * generate_acktimeout_event() and . generate_timeout_event(seqnr).
 * 
 * To call these two methods (for implementing timers), the "swe" object should
 * be referred as follows: swe.generate_acktimeout_event(), or
 * swe.generate_timeout_event(seqnr).
 */
