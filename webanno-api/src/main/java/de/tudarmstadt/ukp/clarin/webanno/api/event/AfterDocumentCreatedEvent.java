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
package de.tudarmstadt.ukp.clarin.webanno.api.event;

import org.apache.uima.jcas.JCas;
import org.springframework.context.ApplicationEvent;

import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;

public class AfterDocumentCreatedEvent
    extends ApplicationEvent
{
    private static final long serialVersionUID = 2367163371168212003L;
    
    private final SourceDocument document;
    private final JCas jcas;

    public AfterDocumentCreatedEvent(Object aSource, SourceDocument aDocument, JCas aJCas)
    {
        super(aSource);
        document = aDocument;
        jcas = aJCas;
    }

    public SourceDocument getDocument()
    {
        return document;
    }

    public JCas getJcas()
    {
        return jcas;
    }
}
