/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.event;

import org.apache.uima.cas.text.AnnotationFS;
import org.springframework.context.ApplicationEvent;

import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;

public class SpanDeletedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 5206262614840209407L;
    
    private final SourceDocument document;
    private final String user;
    private final AnnotationFS annotation;
    
    public SpanDeletedEvent(Object aSource, SourceDocument aDocument, String aUser,
            AnnotationFS aAnnotation)
    {
        super(aSource);
        document = aDocument;
        user = aUser;
        annotation = aAnnotation;
    }
    
    public SourceDocument getDocument()
    {
        return document;
    }
    
    public String getUser()
    {
        return user;
    }
    
    public AnnotationFS getAnnotation()
    {
        return annotation;
    }
    
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("SpanDeletedEvent [");
        if (document != null) {
            builder.append("docID=");
            builder.append(document.getId());
            builder.append(", user=");
            builder.append(user);
            builder.append(", ");
        }
        builder.append("span=[");
        builder.append(annotation.getBegin());
        builder.append("-");
        builder.append(annotation.getEnd());
        builder.append("](");
        builder.append(annotation.getCoveredText());
        builder.append(")]");
        return builder.toString();
    }
}
