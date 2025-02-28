/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.engine.jdbc;

import org.hibernate.internal.log.SubSystemLogging;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

import static org.jboss.logging.Logger.Level.WARN;

/**
 * Sub-system logging related to JDBC interactions
 *
 * @author Steve Ebersole
 */
@SubSystemLogging(
		name = JdbcLogging.NAME,
		description = "Logging related to JDBC interactions"
)
@MessageLogger(projectCode = "HHH")
@ValidIdRange(min = 100001, max = 100500)
public interface JdbcLogging extends BasicLogger {
	String NAME = "org.hibernate.orm.jdbc";

	Logger JDBC_LOGGER = Logger.getLogger( NAME );
	JdbcLogging JDBC_MESSAGE_LOGGER = Logger.getMessageLogger( JdbcLogging.class, NAME );

	boolean JDBC_TRACE_ENABLED = JDBC_LOGGER.isTraceEnabled();
	boolean JDBC_DEBUG_ENABLED = JDBC_LOGGER.isDebugEnabled();

	@LogMessage(level = WARN)
	@Message(
			id=100001,
			value = "JDBC driver did not return the expected number of row counts (%s) - expected %s, but received %s"
	)
	void unexpectedRowCounts(String tableName, int expected, int actual);
}
