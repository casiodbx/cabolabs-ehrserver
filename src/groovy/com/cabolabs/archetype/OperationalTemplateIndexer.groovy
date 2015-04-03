package com.cabolabs.archetypeimport org.openehr.am.archetype.Archetypeimport org.openehr.am.archetype.constraintmodel.*import ehr.clinical_documents.IndexDefinitionimport grails.util.Holdersimport groovy.util.slurpersupport.*/* * IndexDefinition generator. */class OperationalTemplateIndexer {      private static String PS = System.getProperty("file.separator")      def template   def indexes = []      // elems?   // [archetypeId:'openEHR-EHR-COMPOSITION.encounter.v1', path:'/context/setting', rmTypeName:'DV_CODED_TEXT']   def dataIndexes = []      // GPathResuilt corresponding to the reference to a root archetyped element   // It helps to get the name for the ELEMENT nodes to create indexes   def currentRoot   // test...   //def paths = []      def indexAll()   {      def path = "opts" + PS // FIXME: external configuration      def repo = new File( path )            if (!repo.exists())  throw new Exception("No existe "+ path)      if (!repo.canRead())  throw new Exception("No se puede leer "+ path)      if (!repo.isDirectory())  throw new Exception("No es un directorio "+ path)            repo.eachFileMatch groovy.io.FileType.FILES, ~/.*\.opt/, { file ->                  println "indexAll file: "+ file.name         index(file)      }   }      def index(File templateFile)   {      if (!templateFile.exists())  throw new Exception("No existe "+ path)      if (!templateFile.canRead())  throw new Exception("No se puede leer "+ path)      if (!templateFile.isFile())  throw new Exception("No es un archivo "+ path)            this.template = new XmlSlurper().parseText( templateFile.getText() ) // GPathResult                  // Create opt index      def templateId = this.template.template_id.value.text()      def concept = this.template.concept.text()      def language = this.template.language.terminology_id.value.text() +"::"+ this.template.language.code_string.text()      def uid = this.template.uid.value.text()            def tidx = new ehr.clinical_documents.OperationalTemplateIndex(         templateId: templateId,         concept: concept,         language: language,         uid: uid      )      if (!tidx.save(flush:true)) println tidx.errors                  // Nombres de las tags hija directas de definition (atributos del root archetype)      // [rm_type_name, occurrences, node_id, attributes, attributes, archetype_id, template_id, term_definitions]      // println "definition attributes: "+ this.template.definition.children().collect { it.name() }           indexObject(this.template.definition, '/', '/')            //println this.paths // test      this.indexes.each { di ->         if (!di.save())         {            println "======================"            println di.templateId +" "+ di.archetypeId +" "+ di.errors            println "======================"         }      }      this.indexes = []   }      /*    * templateFileName es el nombre del archivo sin la extension.    */   def index(String templateFileName)   {      def path = "opts" + PS + templateFileName + ".opt"      def tfile = new File( path )      index(tfile)   }      def indexAttribute(GPathResult node, String parentPath, String archetypePath)   {      if (!node) throw new Exception("Nodo vacio")      def nextPath      def nextArchPath      node.children.each {                  //println "child "+ it.name()         if (parentPath == '/') nextPath = parentPath + node.rm_attribute_name.text()         else nextPath = parentPath +'/'+ node.rm_attribute_name.text()                  if (archetypePath == '/') nextArchPath = archetypePath + node.rm_attribute_name.text()         else nextArchPath = archetypePath +'/'+ node.rm_attribute_name.text()                  indexObject(it, nextPath, nextArchPath)      }   }      /*    * Procesa nodos objeto de la definicion del template.    * node es un elemento C_OBJECT ej. <children xsi:type="C_COMPLEX_OBJECT"> con rm_type_name, node_id, ...    */   def indexObject (GPathResult node, String parentPath, String archetypePath)   {      if (!node) throw new Exception("Nodo vacio")      if (node.'@xsi:type'.text() == "ARCHETYPE_SLOT") return // Avoid slots, FIXME: warn log            //println node.name()      def path = parentPath            //println path            // Archetype Roots will have Term Definitions inside from where the name of the IndexDefinition should be taken.      // Each Archetype Root Terminology is independent from the other Archetype Roots.      //println "Node type: "+ node.'@xsi:type'      if (node.'@xsi:type'.text() == "C_ARCHETYPE_ROOT")      {         //println "Archetype found: "+ node.archetype_id.value.text()                  // Helps to get the name for the indexed ELEMENTs         this.currentRoot = node                  /*         this.currentRoot.term_definitions.each { term ->                        // - at0005 [text: E, description: Educational components offered.]            println "  - "+ term.@code +" "+ term.items.collect { item -> item.@id.text() +": "+ item.text() }                        //term.items.each { item ->            //   println "    + "+ item.@id            //   println "    + "+ item.text()            //}            //println term.items.collect { item -> item.@id.text() +" "+ item.text() }         }         */                  path += '[archetype_id='+ node.archetype_id.value +']' // slot in the path instead of node_id         archetypePath = '/' // archetype root found      }      else      {         if (path != '/' && !node.node_id.isEmpty() && node.node_id.text() != '')         {            path += '['+ node.node_id.text() + ']'            archetypePath += '['+ node.node_id.text() + ']'         }      }            // ===========================================================================      // Instead of indexing leaf nodes, indexing is done at an ELEMENT.value level      //      // If leaf node      //if (node.attributes.isEmpty()) // NodeChild.attributes is a Map of all attributes      if (!node.rm_type_name.isEmpty() && node.rm_type_name.text() == "ELEMENT")      {         // test         //this.paths << path         //println path +' '+ node.rm_type_name.text()                // TODO: index ELEMENT.null_flavour                  // --------------------------------------------------------         // Find node name         def nodeId = node.node_id.text()                  /* reference structure:          * <term_definitions code="at0000">              <items id="text">Tobacco Use Summary</items>              <items id="description">Summary or persisting information about tobacco use or consumption.</items>            </term_definitions>          */         def term = this.currentRoot.term_definitions.find { it.@code.text() == nodeId } // <term_definitions code="at0000">         def description = term.items.find { it.@id.text() == "text" }.text() // <items id="text">Tobacco Use Summary</items>         // --------------------------------------------------------         // Find type of ELEMENT.value                  /* reference structure:          * <attributes xsi:type="C_SINGLE_ATTRIBUTE">              <rm_attribute_name>value</rm_attribute_name>              ...                                                  <children xsi:type="C_COMPLEX_OBJECT">                <rm_type_name>DV_BOOLEAN</rm_type_name>          */         def valueNode = node.attributes.find { it.rm_attribute_name.text() == "value" }         def type = valueNode.children[0].rm_type_name.text() // DV_BOOLEAN                  indexes << new IndexDefinition(           templateId: this.template.template_id.text(),           archetypeId: this.currentRoot.archetype_id.value.text(),           path: path +"/value", // index ELEMENT.value (is always a data type)           archetypePath: archetypePath +"/value",            rmTypeName: type,           name: description         )                  // if ELEMENT, no further processing is needed      }      else // if not ELEMENT continue processing      {         node.attributes.each {            //println "attr "+ it.name()            indexAttribute(it, path, archetypePath) // No pone su nodeID porque es root         }      }      // ===========================================================================            /**      // CObject      def co      def nodeID      def indexPath      def text            this.archetype.physicalPaths().sort().each { path ->              co = this.archetype.node(path)                // No procesa el nodo /         if (!co.getParent()) return                      // Indices de nivel 2 solo para ELEMENT.value         if (co.rmTypeName == "ELEMENT")         {            nodeID = co.nodeID                        if (!nodeID) throw new Exception("No tiene nodeID: ELEMENT indefinido")                     // node name            def locale = Holders.config.app.l10n.locale            def term = this.archetype.ontology.termDefinition(locale, nodeID)            if (!term)            {               //println " + ERROR: termino no definido para el nodo "+ nodeID            }            else            {               //println " + Node name = "+ term.getText()            }                        // FIXME: JAVA REF IMPL los tipos del RM son DvQuantity en lugar de DV_QUANTITY            //println " ~ index "+ co.path() +"/value "+ this.archetype.node( co.path() +"/value" ).rmTypeName                        indexPath = co.path() +"/value"                        indexes << new IndexDefinition(archetypeId: this.archetype.archetypeId.value,                                     path: indexPath,                                     rmTypeName: this.archetype.node( indexPath ).rmTypeName, // type de ELEMENT.value ej. DvQuantity                                     name: term.getText())                        //println ""         }      } // physical paths     */   }}