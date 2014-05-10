/*********************************************************************************   
 *   Copyright 2012 Zaizi Ltd
 *    
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *   
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 ********************************************************************************/
package org.zaizi.alfresco.publishing.marklogic;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.alfresco.repo.node.encryption.MetadataEncryptor;
import org.alfresco.repo.publishing.PublishingModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * Helper Methods for MarkLogic Publishing
 * 
 * @author aayala
 * 
 */
public class MarkLogicPublishingHelper
{
    private MetadataEncryptor encryptor;

    public void setEncryptor(MetadataEncryptor encryptor)
    {
        this.encryptor = encryptor;
    }

    /**
     * Build a httpContext from channel properties
     * 
     * @param channelProperties
     * @return
     */
    public HttpContext getHttpContextFromChannelProperties(Map<QName, Serializable> channelProperties)
    {
        String markLogicUsername = (String) encryptor.decrypt(PublishingModel.PROP_CHANNEL_USERNAME,
                channelProperties.get(PublishingModel.PROP_CHANNEL_USERNAME));
        String markLogicPassword = (String) encryptor.decrypt(PublishingModel.PROP_CHANNEL_PASSWORD,
                channelProperties.get(PublishingModel.PROP_CHANNEL_PASSWORD));

        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(markLogicUsername, markLogicPassword);
        HttpContext context = new BasicHttpContext();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, creds);
        context.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);

        return context;
    }

    /**
     * Build URI for a nodeRef into MarkLogic Server using the channel properties.
     *
     * @param nodeToPublish the node to publish
     * @param channelProperties the channel properties
     * @return the put uri from node ref and channel properties
     * @throws URISyntaxException the uRI syntax exception
     */
    public URI getPutURIFromNodeRefAndChannelProperties(NodeRef nodeToPublish, Map<QName, Serializable> channelProperties)
            throws URISyntaxException
    {
        URI uri = URIUtils.createURI("http", (String) channelProperties.get(MarkLogicPublishingModel.PROP_HOST),
                (Integer) channelProperties.get(MarkLogicPublishingModel.PROP_PORT), MarkLogicPublishingModel.PUBLISH_URI_KEY,
                "uri=" + nodeToPublish.toString(), null);

        return uri;
    }
    
    /**
     * Gets the delete uri from node ref and channel properties.
     *
     * @param nodeToPublish the node to publish
     * @param channelProperties the channel properties
     * @return the delete uri from node ref and channel properties
     * @throws URISyntaxException the uRI syntax exception
     */
    public URI getDeleteURIFromNodeRefAndChannelProperties(NodeRef nodeToPublish, Map<QName, Serializable> channelProperties)
            throws URISyntaxException
    {
        URI uri = URIUtils.createURI("http", (String) channelProperties.get(MarkLogicPublishingModel.PROP_HOST),
                (Integer) channelProperties.get(MarkLogicPublishingModel.PROP_PORT), MarkLogicPublishingModel.UNPUBLISH_URI_KEY,
                "uri=" + nodeToPublish.toString(), null);

        return uri;
    }

}
