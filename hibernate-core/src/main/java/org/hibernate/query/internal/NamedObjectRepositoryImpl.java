/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.query.NamedHqlQueryDefinition;
import org.hibernate.boot.query.NamedNativeQueryDefinition;
import org.hibernate.boot.query.NamedProcedureCallDefinition;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.procedure.spi.NamedCallableQueryMemento;
import org.hibernate.query.NamedQueryValidationException;
import org.hibernate.query.named.NamedObjectRepository;
import org.hibernate.query.named.NamedQueryMemento;
import org.hibernate.query.named.NamedResultSetMappingMemento;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.sql.spi.NamedNativeQueryMemento;
import org.hibernate.query.sqm.spi.NamedSqmQueryMemento;

import org.jboss.logging.Logger;

import static org.hibernate.query.QueryLogging.QUERY_MESSAGE_LOGGER;

/**
 * @author Steve Ebersole
 */
public class NamedObjectRepositoryImpl implements NamedObjectRepository {
	private static final Logger log = Logger.getLogger( NamedObjectRepository.class );

	private final Map<String, NamedSqmQueryMemento> sqmMementoMap;
	private final Map<String, NamedNativeQueryMemento> sqlMementoMap;
	private final Map<String, NamedCallableQueryMemento> callableMementoMap;
	private final Map<String, NamedResultSetMappingMemento> resultSetMappingMementoMap;

	public NamedObjectRepositoryImpl(
			Map<String,NamedSqmQueryMemento> sqmMementoMap,
			Map<String,NamedNativeQueryMemento> sqlMementoMap,
			Map<String,NamedCallableQueryMemento> callableMementoMap,
			Map<String,NamedResultSetMappingMemento> resultSetMappingMementoMap) {
		this.sqmMementoMap = sqmMementoMap;
		this.sqlMementoMap = sqlMementoMap;
		this.callableMementoMap = callableMementoMap;
		this.resultSetMappingMementoMap = resultSetMappingMementoMap;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Named SQM Memento

	@Override
	public NamedSqmQueryMemento getSqmQueryMemento(String queryName) {
		return sqmMementoMap.get( queryName );
	}

	@Override
	public void visitSqmQueryMementos(Consumer<NamedSqmQueryMemento> action) {
		sqmMementoMap.values().forEach( action );
	}

	@Override
	public void registerSqmQueryMemento(String name, NamedSqmQueryMemento descriptor) {
		sqmMementoMap.put( name, descriptor );
		sqlMementoMap.remove( name );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SQL mementos

	@Override
	public NamedNativeQueryMemento getNativeQueryMemento(String queryName) {
		return sqlMementoMap.get( queryName );
	}

	@Override
	public void visitNativeQueryMementos(Consumer<NamedNativeQueryMemento> action) {
		sqlMementoMap.values().forEach( action );
	}

	@Override
	public synchronized void registerNativeQueryMemento(String name, NamedNativeQueryMemento descriptor) {
		sqlMementoMap.put( name, descriptor );
		sqmMementoMap.remove( name );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// callable mementos

	@Override
	public NamedCallableQueryMemento getCallableQueryMemento(String name) {
		return callableMementoMap.get( name );
	}

	@Override
	public void visitCallableQueryMementos(Consumer<NamedCallableQueryMemento> action) {
		callableMementoMap.values().forEach( action );
	}

	@Override
	public synchronized void registerCallableQueryMemento(String name, NamedCallableQueryMemento memento) {
		callableMementoMap.put( name, memento );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Result-set mapping mementos

	@Override
	public NamedResultSetMappingMemento getResultSetMappingMemento(String mappingName) {
		return resultSetMappingMementoMap.get( mappingName );
	}

	@Override
	public void visitResultSetMappingMementos(Consumer<NamedResultSetMappingMemento> action) {
		resultSetMappingMementoMap.values().forEach( action );
	}

	@Override
	public void registerResultSetMappingMemento(String name, NamedResultSetMappingMemento memento) {
		resultSetMappingMementoMap.put( name, memento );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Prepare repository for use

	@Override
	public NamedQueryMemento resolve(
			SessionFactoryImplementor sessionFactory,
			MetadataImplementor bootMetamodel,
			String registrationName) {
		NamedQueryMemento namedQuery = sqlMementoMap.get( registrationName );
		if ( namedQuery != null ) {
			return namedQuery;
		}
		namedQuery = sqmMementoMap.get( registrationName );
		if ( namedQuery != null ) {
			return namedQuery;
		}
		namedQuery = callableMementoMap.get( registrationName );
		if ( namedQuery != null ) {
			return namedQuery;
		}
		final NamedHqlQueryDefinition namedHqlQueryDefinition = bootMetamodel.getNamedHqlQueryMapping( registrationName );
		if ( namedHqlQueryDefinition != null ) {
			final NamedSqmQueryMemento resolved = namedHqlQueryDefinition.resolve( sessionFactory );
			sqmMementoMap.put( namedHqlQueryDefinition.getRegistrationName(), resolved );
			return resolved;
		}
		final NamedNativeQueryDefinition namedNativeQueryDefinition = bootMetamodel.getNamedNativeQueryMapping( registrationName );
		if ( namedNativeQueryDefinition != null ) {
			final NamedNativeQueryMemento resolved = namedNativeQueryDefinition.resolve( sessionFactory );
			sqlMementoMap.put( namedNativeQueryDefinition.getRegistrationName(), resolved );
			return resolved;
		}
		final NamedProcedureCallDefinition namedCallableQueryDefinition = bootMetamodel.getNamedProcedureCallMapping( registrationName );
		if ( namedCallableQueryDefinition != null ) {
			final NamedCallableQueryMemento resolved = namedCallableQueryDefinition.resolve( sessionFactory );
			callableMementoMap.put( namedCallableQueryDefinition.getRegistrationName(), resolved );
			return resolved;
		}
		return null;
	}

	@Override
	public void prepare(SessionFactoryImplementor sessionFactory, Metadata bootMetamodel) {
		bootMetamodel.visitNamedHqlQueryDefinitions(
				namedHqlQueryDefinition -> {
					final NamedSqmQueryMemento resolved = namedHqlQueryDefinition.resolve( sessionFactory );
					sqmMementoMap.put( namedHqlQueryDefinition.getRegistrationName(), resolved );
				}
		);

		bootMetamodel.visitNamedNativeQueryDefinitions(
				namedNativeQueryDefinition -> {
					final NamedNativeQueryMemento resolved = namedNativeQueryDefinition.resolve( sessionFactory );
					sqlMementoMap.put( namedNativeQueryDefinition.getRegistrationName(), resolved );
				}
		);

		bootMetamodel.visitNamedResultSetMappingDefinition(
				namedResultSetMappingDefinition -> {
					final NamedResultSetMappingMemento resolved = namedResultSetMappingDefinition.resolve( () -> sessionFactory );
					resultSetMappingMementoMap.put( namedResultSetMappingDefinition.getRegistrationName(), resolved );
				}
		);

		bootMetamodel.visitNamedProcedureCallDefinition(
				namedProcedureCallDefinition -> {
					final NamedCallableQueryMemento resolved = namedProcedureCallDefinition.resolve( sessionFactory );
					callableMementoMap.put( namedProcedureCallDefinition.getRegistrationName(), resolved );
				}
		);

	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Named query checking

	@Override
	public void validateNamedQueries(QueryEngine queryEngine) {
		final Map<String, HibernateException> errors = checkNamedQueries( queryEngine );
		if ( !errors.isEmpty() ) {
			int i = 0;
			final StringBuilder failingQueries = new StringBuilder( "Errors in named queries: " );
			for ( Map.Entry<String, HibernateException> entry : errors.entrySet() ) {
				QUERY_MESSAGE_LOGGER.namedQueryError( entry.getKey(), entry.getValue() );
				failingQueries.append( "\n" )
						.append("  [").append(++i).append("] Error in query named '").append( entry.getKey() ).append("'")
						.append(": ").append( entry.getValue().getMessage() );
			}
			final NamedQueryValidationException exception =
					new NamedQueryValidationException( failingQueries.toString(), errors );
			errors.values().forEach( exception::addSuppressed );
			throw exception;
		}
	}

	@Override
	public Map<String, HibernateException> checkNamedQueries(QueryEngine queryEngine) {
		Map<String,HibernateException> errors = new HashMap<>();

		final QueryInterpretationCache interpretationCache = queryEngine.getInterpretationCache();

		// Check named HQL queries
		log.debugf( "Checking %s named HQL queries", sqmMementoMap.size() );
		for ( NamedSqmQueryMemento hqlMemento : sqmMementoMap.values() ) {
			try {
				log.debugf( "Checking named HQL query: %s", hqlMemento.getRegistrationName() );
				String queryString = hqlMemento.getHqlString();
				interpretationCache.resolveHqlInterpretation(
						queryString,
						null,
						s -> queryEngine.getHqlTranslator().translate( queryString, null )
				);
			}
			catch ( HibernateException e ) {
				errors.put( hqlMemento.getRegistrationName(), e );
			}
		}

		// Check native-sql queries
		log.debugf( "Checking %s named SQL queries", sqlMementoMap.size() );
		for ( NamedNativeQueryMemento memento : sqlMementoMap.values() ) {
			memento.validate( queryEngine );
//			// this will throw an error if there's something wrong.
//			try {
//				log.debugf( "Checking named SQL query: %s", memento.getRegistrationName() );
//				// TODO : would be really nice to cache the spec on the query-def so as to not have to re-calc the hash;
//				// currently not doable though because of the resultset-ref stuff...
//				NativeSQLQuerySpecification spec;
//				if ( memento.getResultSetMappingName() != null ) {
//					NamedResultSetMappingMemento resultSetMappingMemento = getResultSetMappingMemento( memento.getResultSetMappingName() );
//					if ( resultSetMappingMemento == null ) {
//						throw new MappingException( "Unable to find resultset-ref resultSetMappingMemento: " + memento.getResultSetMappingName() );
//					}
//					spec = new NativeSQLQuerySpecification(
//							namedSQLQueryDefinition.getQueryString(),
//							resultSetMappingMemento.getQueryReturns(),
//							namedSQLQueryDefinition.getQuerySpaces()
//					);
//				}
//				else {
//					spec =  new NativeSQLQuerySpecification(
//							namedSQLQueryDefinition.getQueryString(),
//							namedSQLQueryDefinition.getQueryReturns(),
//							namedSQLQueryDefinition.getQuerySpaces()
//					);
//				}
//				queryEngine.getNativeSQLQueryPlan( spec );
//			}
//			catch ( HibernateException e ) {
//				errors.put( namedSQLQueryDefinition.getName(), e );
//			}
		}

		return errors;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Shutdown

	@Override
	public void close() {
		sqmMementoMap.clear();
		sqlMementoMap.clear();
		callableMementoMap.clear();
		resultSetMappingMementoMap.clear();
	}
}
