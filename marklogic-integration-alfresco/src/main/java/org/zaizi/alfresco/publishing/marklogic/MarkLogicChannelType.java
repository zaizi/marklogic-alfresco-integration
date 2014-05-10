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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.publishing.AbstractChannelType;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.alfresco.util.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Channel definition for publishing/unpublishing XML content to MarkLogic Server
 * 
 * @author aayala
 * 
 */
public class MarkLogicChannelType extends AbstractChannelType
{
    private final static Log log = LogFactory.getLog(MarkLogicChannelType.class);

    public final static String ID = "marklogic";
    private final static int STATUS_DOCUMENT_INSERTED = 204;
    private final static int STATUS_DOCUMENT_DELETED = 200;
    private final static Set<String> DEFAULT_SUPPORTED_MIME_TYPES = CollectionUtils.unmodifiableSet(MimetypeMap.MIMETYPE_XML);

    private MarkLogicPublishingHelper publishingHelper;
    private ContentService contentService;

    private Set<String> supportedMimeTypes = DEFAULT_SUPPORTED_MIME_TYPES;

    public void setSupportedMimeTypes(Set<String> mimeTypes)
    {
        supportedMimeTypes = Collections.unmodifiableSet(new TreeSet<String>(mimeTypes));
    }

    public void setPublishingHelper(MarkLogicPublishingHelper markLogicPublishingHelper)
    {
        this.publishingHelper = markLogicPublishingHelper;
    }

    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService;
    }

    public boolean canPublish()
    {
        return true;
    }

    public boolean canPublishStatusUpdates()
    {
        return false;
    }

    public boolean canUnpublish()
    {
        return true;
    }

    public QName getChannelNodeType()
    {
        return MarkLogicPublishingModel.TYPE_DELIVERY_CHANNEL;
    }

    public String getId()
    {
        return ID;
    }

    @Override
    public Set<String> getSupportedMimeTypes()
    {
        return supportedMimeTypes;
    }

    @Override
    public void publish(NodeRef nodeToPublish, Map<QName, Serializable> channelProperties)
    {
        ContentReader reader = contentService.getReader(nodeToPublish, ContentModel.PROP_CONTENT);
        if (reader.exists())
        {
            File contentFile;
            boolean deleteContentFileOnCompletion = false;
            if (FileContentReader.class.isAssignableFrom(reader.getClass()))
            {
                // Grab the content straight from the content store if we can...
                contentFile = ((FileContentReader) reader).getFile();
            }
            else
            {
                // ...otherwise copy it to a temp file and use the copy...
                File tempDir = TempFileProvider.getLongLifeTempDir("marklogic");
                contentFile = TempFileProvider.createTempFile("marklogic", "", tempDir);
                reader.getContent(contentFile);
                deleteContentFileOnCompletion = true;
            }

            HttpClient httpclient = new DefaultHttpClient();
            try
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Publishing node: " + nodeToPublish);
                }

                URI uriPut = publishingHelper.getPutURIFromNodeRefAndChannelProperties(nodeToPublish, channelProperties);

                HttpPut httpput = new HttpPut(uriPut);
                FileEntity filenEntity = new FileEntity(contentFile, MimetypeMap.MIMETYPE_XML);
                httpput.setEntity(filenEntity);

                HttpResponse response = httpclient.execute(httpput,
                        publishingHelper.getHttpContextFromChannelProperties(channelProperties));

                if (log.isDebugEnabled())
                {
                    log.debug("Response Status: " + response.getStatusLine().getStatusCode() + " - Message: "
                            + response.getStatusLine().getReasonPhrase() + " - NodeRef: " + nodeToPublish.toString());
                }
                if (response.getStatusLine().getStatusCode() != STATUS_DOCUMENT_INSERTED)
                {
                    throw new AlfrescoRuntimeException(response.getStatusLine().getReasonPhrase());
                }
            }
            catch (IllegalStateException e)
            {
                throw new AlfrescoRuntimeException(e.getLocalizedMessage());
            }
            catch (IOException e)
            {
                throw new AlfrescoRuntimeException(e.getLocalizedMessage());
            }
            catch (URISyntaxException e)
            {
                throw new AlfrescoRuntimeException(e.getLocalizedMessage());
            }
            finally
            {
                httpclient.getConnectionManager().shutdown();
                if (deleteContentFileOnCompletion)
                {
                    contentFile.delete();
                }
            }
        }
    }

    @Override
    public void unpublish(NodeRef nodeToUnpublish, Map<QName, Serializable> channelProperties)
    {
        HttpClient httpclient = new DefaultHttpClient();
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("Unpublishing node: " + nodeToUnpublish);
            }

            URI uriDelete = publishingHelper.getDeleteURIFromNodeRefAndChannelProperties(nodeToUnpublish, channelProperties);

            HttpDelete httpDelete = new HttpDelete(uriDelete);

            HttpResponse response = httpclient.execute(httpDelete,
                    publishingHelper.getHttpContextFromChannelProperties(channelProperties));

            log.info("Response Status: " + response.getStatusLine().getStatusCode() + " - Message: "
                    + response.getStatusLine().getReasonPhrase() + " - NodeRef: " + nodeToUnpublish.toString());

            if (response.getStatusLine().getStatusCode() != STATUS_DOCUMENT_DELETED)
            {
                throw new AlfrescoRuntimeException(response.getStatusLine().getReasonPhrase());
            }
        }
        catch (IllegalStateException e)
        {
            throw new AlfrescoRuntimeException(e.getLocalizedMessage());
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getLocalizedMessage());
        }
        catch (URISyntaxException e)
        {
            throw new AlfrescoRuntimeException(e.getLocalizedMessage());
        }
        finally
        {
            httpclient.getConnectionManager().shutdown();
        }

    }
}
