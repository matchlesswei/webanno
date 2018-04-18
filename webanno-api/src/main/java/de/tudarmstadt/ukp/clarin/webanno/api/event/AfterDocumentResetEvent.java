/*
 * Copyright 2018
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

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;

public class AfterDocumentResetEvent
    extends ApplicationEvent
{
    private static final long serialVersionUID = 686641613168415460L;
    
    private final AnnotationDocument document;
    private final JCas jcas;

    public AfterDocumentResetEvent(Object aSource, AnnotationDocument aDocument, JCas aJCas)
    {
        super(aSource);
        document = aDocument;
        jcas = aJCas;
    }

    public AnnotationDocument getDocument()
    {
        return document;
    }

    public JCas getJCas()
    {
        return jcas;
    }
}
