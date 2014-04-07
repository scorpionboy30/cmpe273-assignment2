package edu.sjsu.cmpe.library.domain;

import javax.jms.Connection;

public class Util {
	private Connection connection;

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}
}
