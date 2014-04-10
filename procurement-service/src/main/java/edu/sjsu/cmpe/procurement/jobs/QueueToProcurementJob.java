package edu.sjsu.cmpe.procurement.jobs;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;
import edu.sjsu.cmpe.procurement.config.ProcurementServiceConfiguration;

/**
 * This job will run at every 5 second.
 */
@Every("5s")
public class QueueToProcurementJob extends Job {
	public static ProcurementServiceConfiguration configuration;
	
	@Override
	public void doJob() {
		Connection connection = null;
		Session session = null;
		MessageConsumer consumer = null;
		try {
			StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
			factory.setBrokerURI("tcp://" + configuration.getApolloHost() + ":" + configuration.getApolloPort());
			connection = factory.createConnection(configuration.getApolloUser(), configuration.getApolloPassword());

			connection.start();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination dest = new StompJmsDestination("/queue/80067.book.orders");

			consumer = session.createConsumer(dest);
			System.out.println("Waiting for messages from /queue/80067.book.orders...");
			long waitUntil = 5000; // wait for 5 sec
			while (true) {
				Message msg = consumer.receive(waitUntil);
				if (msg instanceof TextMessage) {
					String body = ((TextMessage) msg).getText();
					System.out.println("Received order = " + body);

					int isbn = Integer.parseInt(body.split(":")[1]);
					ProcurementService.isbns.add(isbn);

				} else if (msg == null) {
					System.out
							.println("No new orders. Exiting due to timeout - "
									+ waitUntil / 1000 + " sec");
					break;
				} else {
					System.out.println("Unexpected message type: "
							+ msg.getClass());
				}
			}
			System.out.println("Done");
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally{
			try{
				consumer.close();
				session.close();
				connection.stop();
				connection.close();
			}catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
