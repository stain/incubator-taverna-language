package org.purl.wf4ever.wfdesc.scufl;

import static org.junit.Assert.assertNotNull;
import static org.purl.wf4ever.wfdesc.scufl2.WfdescReader.TEXT_VND_WF4EVER_WFDESC_TURTLE;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.memory.MemoryStore;

import uk.org.taverna.scufl2.api.container.WorkflowBundle;
import uk.org.taverna.scufl2.api.io.ReaderException;
import uk.org.taverna.scufl2.api.io.WorkflowBundleIO;
import uk.org.taverna.scufl2.translator.t2flow.T2FlowReader;

public class TestSemanticAnnotations {
	private static final String IMAGEMAGICKCONVERT_T2FLOW = "/valid_component_imagemagickconvert.t2flow";
    protected WorkflowBundle workflowBundle;
	protected WorkflowBundleIO bundleIO = new WorkflowBundleIO();

	@Before
	public void loadWorkflow() throws ReaderException, IOException {
	    URL t2flow = getClass().getResource(IMAGEMAGICKCONVERT_T2FLOW);
	    assertNotNull("Could not find resource " + IMAGEMAGICKCONVERT_T2FLOW, t2flow);
		workflowBundle = bundleIO.readBundle(t2flow, T2FlowReader.APPLICATION_VND_TAVERNA_T2FLOW_XML);
	}
	

	public File tempFile() throws IOException {
		File bundleFile = File.createTempFile("wfdesc", ".ttl");
//		bundleFile.deleteOnExit();
		System.out.println(bundleFile);
		return bundleFile;
	}

	@Test
	public void writeBundleToFile() throws Exception {
		File bundleFile = tempFile();
		bundleIO.writeBundle(workflowBundle, bundleFile,
				TEXT_VND_WF4EVER_WFDESC_TURTLE);
		FileUtils.readFileToString(bundleFile, "utf-8");
		
		Repository myRepository = new SailRepository(new MemoryStore());
		myRepository.initialize();
		RepositoryConnection con = myRepository.getConnection();
		con.add(bundleFile, bundleFile.toURI().toASCIIString(), RDFFormat.TURTLE);
//		assertTrue(con.prepareTupleQuery(QueryLanguage.SPARQL,
//		assertTrue(con.prepareBooleanQuery(QueryLanguage.SPARQL, 
//				"PREFIX wfdesc: <http://purl.org/wf4ever/wfdesc#>  " +
//				"ASK { " +
//				"?wf a wfdesc:Workflow, wfdesc:Process ;" +
//				"  wfdesc:hasInput ?yourName; " +
//				"  wfdesc:hasOutput ?results; " +
//				"  wfdesc:hasDataLink ?link1; " +
//				"  wfdesc:hasDataLink ?link2; " +
//				"  wfdesc:hasDataLink ?link3; " +
//				"  wfdesc:hasSubProcess ?hello ; " +
//				"  wfdesc:hasSubProcess ?wait4me . " +
//				"?hello a wfdesc:Process ;" +
//				"  wfdesc:hasInput ?name; " +
//				"  wfdesc:hasOutput ?greeting . " +
//				"?wait4me a wfdesc:Process ." +
//				"?yourName a wfdesc:Input . " +
//				"?results a wfdesc:Output . " +
//				"?name a wfdesc:Input . " +
//				"?greeting a wfdesc:Output . " +
//				"?link1 a wfdesc:DataLink ; " +
//				"  wfdesc:hasSource ?yourName ; " +
//				"  wfdesc:hasSink ?results . " +
//				"?link2 a wfdesc:DataLink ; " +
//				"  wfdesc:hasSource ?yourName ; " +
//				"  wfdesc:hasSink ?name . " +
//				"?link3 a wfdesc:DataLink ; " +
//				"  wfdesc:hasSource ?greeting ; " +
//				"  wfdesc:hasSink ?results ." +
//			    "}").evaluate());
	}

}
