package edu.sjsu.cmpe.library.listener;

import java.net.URL;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsMessage;

import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;

public class BookListener implements Runnable {
	public static LibraryServiceConfiguration configuration;
	public static BookRepositoryInterface bookRepository;

	@Override
	public void run() {
		try {
			StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
			factory.setBrokerURI("tcp://" + configuration.getApolloHost() + ":"
					+ configuration.getApolloPort());
			Connection connection = factory.createConnection(
					configuration.getApolloUser(),
					configuration.getApolloPassword());
			connection.start();
			Destination dest = new StompJmsDestination(
					configuration.getStompTopicName());
			Session session = connection.createSession(false,
					Session.AUTO_ACKNOWLEDGE);
			MessageConsumer consumer = session.createConsumer(dest);
			String receivedMsg = null;

			while (true) {
				Message msg = consumer.receive();
				if (msg != null) {
					if (msg instanceof TextMessage) {
						receivedMsg = ((TextMessage) msg).getText();
						System.out.println("TextMessage===>" + receivedMsg);

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

						if (recMsgArr.length > 2) {
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
							} catch (Exception e) {
								e.printStackTrace();
							}
							bookRepository.updateLibrary(receivedBook);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
