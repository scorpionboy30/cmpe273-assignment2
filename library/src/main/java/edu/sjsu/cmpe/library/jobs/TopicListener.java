package edu.sjsu.cmpe.library.jobs;

import java.net.MalformedURLException;
import java.net.URL;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsMessage;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;

@Every("350s")
public class TopicListener extends Job {
	public static LibraryServiceConfiguration configuration;
	public static BookRepositoryInterface bookRepository;

	@Override
	public void doJob() {
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + configuration.getApolloHost() + ":"
				+ configuration.getApolloPort());
		Connection connection = null;
		try {
			connection = factory.createConnection(
					configuration.getApolloUser(),
					configuration.getApolloPassword());
			connection.start();
			Destination dest = new StompJmsDestination(
					configuration.getStompTopicName());
			Session session = connection.createSession(false,
					Session.AUTO_ACKNOWLEDGE);
			MessageConsumer consumer = session.createConsumer(dest);
			long waitUntil = 5000;
			String receivedMsg = null;
			while (true) {
				Message msg = consumer.receive(waitUntil);
				if (msg != null) {
					if (msg instanceof TextMessage) {
						receivedMsg = ((TextMessage) msg).getText();
						System.out.println("StompJmsMessage===>" + receivedMsg);

					} else if (msg instanceof StompJmsMessage) {
						StompJmsMessage smsg = ((StompJmsMessage) msg);
						receivedMsg = smsg.getFrame().contentAsString();
						System.out.println("StompJmsMessage===>" + smsg);

					} else {
						System.out.println("Unexpected message type: "
								+ msg.getClass());
					}

					if (receivedMsg != null) {
						String recMsgArr[] = receivedMsg.split("\"");

						String isbn = recMsgArr[0].split(":")[0];
						String title = recMsgArr[1];
						String category = recMsgArr[3];
						String coverImage = recMsgArr[5];
						Book receivedBook = new Book();

						receivedBook.setIsbn(Long.parseLong(isbn));
						receivedBook.setTitle(title);
						receivedBook.setCategory(category);
						try {
							receivedBook.setCoverimage(new URL(coverImage));
						} catch (MalformedURLException e) {
							e.printStackTrace();
						}
						bookRepository.updateLibrary(receivedBook);
					}
				}// end of if
			}
		} catch (JMSException e) {
			e.printStackTrace();
		}
		try {
			connection.close();
		} catch (JMSException e) {
			e.printStackTrace();
		}
	}

}
