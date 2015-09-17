/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.amherst.acdc.idiomatic;

import static org.apache.camel.builder.PredicateBuilder.not;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.PropertyInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;

/**
 * A content router for handling JMS events.
 *
 * @author Aaron Coburn
 */
public class EventRouter extends RouteBuilder {

    private static final String CQL_INSERT = "insert into uris(fedora, public) values (?, ?)";
    private static final String CQL_GET = "";
    private static final String CQL_DELETE = "";

    @PropertyInject(value = "rest.port", defaultValue = "9081")
    private String port;

    /**
     * Configure the message route workflow.
     */
    public void configure() throws Exception {

        final Namespaces ns = new Namespaces("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

        try {
            final String property = getContext().resolvePropertyPlaceholders("{{id.property}}");
            final String namespace = getContext().resolvePropertyPlaceholders("{{id.namespace}}");
            final String prefix = property.substring(0, property.indexOf(":"));
            ns.add(prefix, namespace);

        } catch (final Exception ex) {
            throw new RuntimeCamelException("Could not resolve property placeholders", ex);
        }

        /**
         * A generic error handler (specific to this RouteBuilder)
         */
        onException(Exception.class)
            .maximumRedeliveries("{{error.maxRedeliveries}}")
            .log("Event Routing Error: ${routeId}");

        /**
         * Process a message via JMS
         */
        from("{{input.stream}}")
            .routeId("IdMappingRouter")
            .to("direct:event");

        from("direct:event")
            .routeId("IdMappingEventRouter")
            .log(LoggingLevel.INFO, "edu.amherst.acdc.idiomatic",
                    "IdMapping Event: ${headers[org.fcrepo.jms.identifier]}")
            .to("fcrepo:localhost:8080/rest?preferOmit=PreferContainment")
            .split().xtokenize("//{{id.property}}", 'i', ns)
              .transform().xpath("/{{id.property}}/@rdf:resource | /{{id.property}}/text()", String.class, ns)
              .process(new IdProcessor())
              .to("direct:update");

        /**
         * REST routing
         */
        rest("{{rest.prefix}}")
            .get("/{id}").to("direct:get")
            .post("/").to("direct:minter")
            .put("/{id}").to("direct:update")
            .delete("/{id}").to("direct:delete");

        /**
         * Handle CRUD operations
         */
        from("direct:update")
            .routeId("IdMappingUpdateRouter")
            .to("sql:UPDATE uris SET fedora=:#fedora WHERE public=:#public")
            .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(204)
            .filter(simple("${body} == 0"))
              .to("sql:INSERT INTO uris (fedora, public) VALUES (:#fedora, :#public)");

        from("direct:get")
            .routeId("IdMappingFetchRouter")
            .to("sql:SELECT fedora FROM uris WHERE public=:#id?outputType=SelectOne")
            .removeHeader("id")
            .filter(not(header("CamelSqlRowCount").isEqualTo(1)))
                .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(404);

        from("direct:delete")
            .routeId("IdMappingDeleteRouter")
            .to("sql:DELETE FROM uris WHERE public=:#id")
            .removeHeader("id")
            .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(204);

    }
}
