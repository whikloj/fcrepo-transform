/*
 * Copyright 2015 DuraSpace, Inc.
 *
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
package org.fcrepo.integration;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.fcrepo.transform.transformations.LDPathTransform.APPLICATION_RDF_LDPATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * <p>FedoraTransformIT class.</p>
 *
 * @author cbeer
 */
@ContextConfiguration({"/spring-test/test-container.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class FedoraTransformIT extends AbstractResourceIT {

    // This regex represents the following pattern: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
    //  see: JsonObjectProvider.DATE_FORMAT
    private final String DATE_TIME_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

    @Test
    public void testLdpathWithDefaultProgram() throws IOException {

        final String pid = "testLdpathWithConfiguredProgram-" + randomUUID();
        createObject(pid);
        final HttpGet postLdpathProgramRequest
                = new HttpGet(serverAddress + "/" + pid + "/fcr:transform/default");
        final HttpResponse response = client.execute(postLdpathProgramRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());
        final String content = EntityUtils.toString(response.getEntity());
        logger.debug("Retrieved ldpath feed:\n" + content);

        final JsonNode rootNode = new ObjectMapper().readTree(new JsonFactory().createParser(content));

        assertEquals("Failed to retrieve correct identifier in JSON!", serverAddress + "/" + pid,
                rootNode.get(0).get("id").elements().next().asText());

        final JsonNode creationDateJson = rootNode.get(0).get("created");
        assertNotNull(creationDateJson);

        final JsonNode dateNode = creationDateJson.get(0);
        assertNotNull(dateNode);
        assertTrue(dateNode.asText() + " should be of format: " + DATE_TIME_REGEX,
                dateNode.asText().matches(DATE_TIME_REGEX));

    }

    @Test
    public void testLdpathWithDeluxeProgram() throws IOException {

        final String pid = "testLdpathWithDeluxeProgram-" + randomUUID();
        createObject(pid);
        final HttpGet postLdpathProgramRequest
                = new HttpGet(serverAddress + "/" + pid + "/fcr:transform/deluxe");
        final HttpResponse response = client.execute(postLdpathProgramRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());
        final String content = EntityUtils.toString(response.getEntity());
        logger.debug("Retrieved ldpath feed:\n" + content);

        final JsonNode rootNode = new ObjectMapper().readTree(new JsonFactory().createParser(content));

        assertEquals("Failed to retrieve correct identifier in JSON!", serverAddress + "/" + pid,
                rootNode.get(0).get("id").elements().next().asText());

        assertNotNull(rootNode.get(0).get("createdBy"));
        assertNotNull(rootNode.get(0).get("hasParent"));
        assertNotNull(rootNode.get(0).get("hasVersions"));
        assertNotNull(rootNode.get(0).get("lastModified"));
        assertNotNull(rootNode.get(0).get("lastModifiedBy"));
        assertNotNull(rootNode.get(0).get("numberOfChildren"));
        assertNotNull(rootNode.get(0).get("type"));
        assertNotNull(rootNode.get(0).get("prefLabel"));
        assertNotNull(rootNode.get(0).get("altLabel"));

        final JsonNode creationDateJson = rootNode.get(0).get("created");
        assertNotNull(creationDateJson);

        final JsonNode dateNode = creationDateJson.get(0);
        assertNotNull(dateNode);
        assertTrue(dateNode.asText() + " should be of format: " + DATE_TIME_REGEX,
                dateNode.asText().matches(DATE_TIME_REGEX));

    }

    @Test
    public void testLdpathWithProgramBody() throws ParseException, IOException {

        final String pid = UUID.randomUUID().toString();
        createObject(pid);

        final HttpPost postLdpathProgramRequest = new HttpPost(serverAddress + "/" + pid + "/fcr:transform");
        final BasicHttpEntity e = new BasicHttpEntity();

        final String s = "id = . :: xsd:string ;\n";

        e.setContent(new ByteArrayInputStream(s.getBytes()));

        postLdpathProgramRequest.setEntity(e);
        postLdpathProgramRequest.setHeader("Content-Type", APPLICATION_RDF_LDPATH);
        final HttpResponse response = client.execute(postLdpathProgramRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());
        final String content = EntityUtils.toString(response.getEntity());
        logger.debug("Retrieved LDPath result:\n" + content);

        final JsonNode rootNode = new ObjectMapper().readTree(new JsonFactory().createParser(content));

        assertEquals("Failed to retrieve correct identifier in JSON!", serverAddress + "/" + pid, rootNode
                .get(0).get("id").elements().next().asText());

    }

    @Test
    public void testMakeReferenceToTransformSpace() throws IOException {
        final String pid = UUID.randomUUID().toString();
        createObject(pid);

        final HttpGet getTransformRequest = new HttpGet(serverAddress + "/" + pid + "/fcr:transform/default");
        try (final CloseableHttpResponse getResponse = (CloseableHttpResponse) client.execute(getTransformRequest)) {
            assertEquals("Can't get default transform", OK.getStatusCode(),
                    getResponse.getStatusLine().getStatusCode());
        }

        final String pid2 = UUID.randomUUID().toString();
        final HttpPut putReferenceRequest = new HttpPut(serverAddress + "/" + pid2);

        final InputStream turtleFile = this.getClass().getResourceAsStream("/referenceTest.ttl");
        final InputStreamEntity entity = new InputStreamEntity(turtleFile);
        putReferenceRequest.setEntity(entity);
        putReferenceRequest.setHeader("Content-type", "text/turtle");

        try (final CloseableHttpResponse putResponse = (CloseableHttpResponse) client.execute(putReferenceRequest)) {
            assertEquals("Can't make reference to /fedora:system/fedora:transform", CREATED.getStatusCode(),
                    putResponse.getStatusLine().getStatusCode());
        }
    }
}
