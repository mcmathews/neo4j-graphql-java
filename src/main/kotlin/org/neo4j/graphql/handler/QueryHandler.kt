package org.neo4j.graphql.handler

import graphql.language.*
import graphql.schema.DataFetchingEnvironment
import org.neo4j.graphql.*

class QueryHandler private constructor(
        type: NodeFacade,
        fieldDefinition: FieldDefinition,
        metaProvider: MetaProvider)
    : BaseDataFetcher(type, fieldDefinition, metaProvider) {

    companion object {
        fun build(type: NodeFacade, filterTypeName: String, orderingTypename: String, metaProvider: MetaProvider): BaseDataFetcher? {
            val typeName = type.name()
            val relevantFields = type.relevantFields()

            val fieldDefinition = FieldDefinition
                .newFieldDefinition()
                .name(typeName.decapitalize())
                .inputValueDefinitions(getInputValueDefinitions(relevantFields) { true })
                .inputValueDefinition(input(FILTER, TypeName(filterTypeName)))
                .inputValueDefinition(input(ORDER_BY, TypeName(orderingTypename)))
                .inputValueDefinition(input(FIRST, TypeName("Int")))
                .inputValueDefinition(input(OFFSET, TypeName("Int")))
                .type(NonNullType(ListType(NonNullType(TypeName(typeName)))))
                .build()
            return QueryHandler(type, fieldDefinition, metaProvider)
        }

        fun build(fieldDefinition: FieldDefinition,
                isQuery: Boolean,
                metaProvider: MetaProvider,
                type: NodeFacade = metaProvider.getNodeType(fieldDefinition.type.name())
                        ?: throw IllegalStateException("cannot find type " + fieldDefinition.type.name())
        ): BaseDataFetcher? {
            val cypherDirective = fieldDefinition.cypherDirective()
            return when {
                cypherDirective != null -> CypherDirectiveHandler(type, isQuery, cypherDirective, fieldDefinition, metaProvider)
                isQuery -> QueryHandler(type, fieldDefinition, metaProvider)
                else -> null
            }
        }
    }

    override fun generateCypher(variable: String, field: Field, projectionProvider: () -> Cypher, env: DataFetchingEnvironment): Cypher {

        val mapProjection = projectionProvider.invoke()
        val ordering = orderBy(variable, field.arguments)
        val skipLimit = format(skipLimit(field.arguments))

        val select = if (type.isRelationType()) {
            "()-[$variable:${label()}]->()"
        } else {
            "($variable:${label()})"
        }
        val where = where(variable, fieldDefinition, type, propertyArguments(field), field)
        return Cypher("MATCH $select${where.query}" +
                " RETURN ${mapProjection.query} AS $variable$ordering${skipLimit}",
                (where.params + mapProjection.params))
    }
}