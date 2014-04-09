package edu.sjsu.cmpe.procurement.jobs;

import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;
import edu.sjsu.cmpe.procurement.config.ProcurementServiceConfiguration;

/**
 * This job will run at every 5 minutes.
 */
@Every("300s")
public class ProcurementToPublisherJob extends Job {
	public static ProcurementServiceConfiguration configuration;

	@Override
	public void doJob() {
		// try {
		// TODO Auto-generated method stub
		System.out.println("Executing the 5 minute job");
		System.out.println("*******************Get Data from Queue!!*******************");
		
		if (ProcurementService.isbns != null
				&& !ProcurementService.isbns.isEmpty()) {
			postMessagesToPublisher();
			ProcurementService.isbns = new ArrayList<Integer>();
			getMSgFromPublisher();
		} else {
			System.out.println("Array is empty");
		}
	}

	public void postMessagesToPublisher() {
		try {
			System.out.println("Posting msges to publisher!!");

			Client client = Client.create();
			WebResource webResource = client.resource("http://" + configuration.getApolloHost() +":9000/orders");

			String input = "{\"id\":\"80067\",\"order_book_isbns\":" + ProcurementService.isbns + "}";
			System.out.println("Input JSON created is --->" + input);

			ClientResponse response = webResource.type("application/json").post(ClientResponse.class, input);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
			}
			
			System.out.println("Output from Server .... \n");
			String output = response.getEntity(String.class);
			System.out.println(output);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void getMSgFromPublisher() {
		String jsonString = ProcurementService.jerseyClient
				.resource("http://" + configuration.getApolloHost() + ":9000/orders/80067")
				.type("application/json").get(String.class);
		System.out.println("Jsson==>" + jsonString);

		try {
			JSONObject tempJObj = new JSONObject(jsonString);
			JSONArray jArray = tempJObj.getJSONArray("shipped_books");
			for (int i = 0; i < jArray.length(); i++) { // **line 2**
				JSONObject childJSONObject = jArray.getJSONObject(i);
				String categoryName = childJSONObject.getString("category");
				System.out.println("Category is-->" + categoryName);
				String tempJSON = childJSONObject.getString("isbn") + ":\""
						+ childJSONObject.getString("title") + "\":" + "\""
						+ childJSONObject.getString("category") + "\":" + "\""
						+ childJSONObject.getString("coverimage") + "\"";
				System.out.println("Newly created JSON as per fomat is---->\n"+ tempJSON);
				if (tempJSON != null) {
					String topicName = configuration.getStompTopicPrefix();
					Destination dest = null;
					if ("computer".equalsIgnoreCase(categoryName)) {
						//push books to computer topic
						topicName = topicName + configuration.getStompTopicComputer();
						dest = new StompJmsDestination(topicName);
						pushBooksToTopics(tempJSON, categoryName, dest);
					}
					//push books to all topic
					topicName = configuration.getStompTopicPrefix() + configuration.getStompTopicAll();
					dest = new StompJmsDestination(topicName);
					pushBooksToTopics(tempJSON, categoryName, dest);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void pushBooksToTopics(String tempJSON, String categoryName, Destination dest) {
		System.out.println("Inside pushBooksToTopics");
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + configuration.getApolloHost() + ":"
				+ configuration.getApolloPort());
		Connection connection= null;
		Session session = null;
		MessageProducer producer = null;
		try {
			connection = factory.createConnection(configuration.getApolloUser(),
					configuration.getApolloPassword());
			connection.start();
			
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			producer = session.createProducer(dest);
			producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

			TextMessage msg = session.createTextMessage(tempJSON);
			msg.setLongProperty("id", System.currentTimeMillis());
			producer.send(msg);
			System.out.println("Msg sent to topics");
			
			
		} catch (JMSException e) {
			e.printStackTrace();
		}
		finally{
			try{
			producer.close();
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
