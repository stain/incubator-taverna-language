package org.apache.taverna.scufl2.translator.t2flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URL;

import org.apache.taverna.scufl2.api.common.Scufl2Tools;
import org.apache.taverna.scufl2.api.configurations.Configuration;
import org.apache.taverna.scufl2.api.container.WorkflowBundle;
import org.apache.taverna.scufl2.api.core.Processor;
import org.apache.taverna.scufl2.api.profiles.Profile;
import org.apache.taverna.scufl2.translator.t2flow.T2FlowParser;
import org.junit.Test;


public class TestT2FlowParser {

	private static final String INTERACTION_WITH_LOOP = "/interaction-with-strange-loop.t2flow";
	private static final String AS_T2FLOW = "/as.t2flow";
	// Workflows from myExperiment that had issues being parsed in 0.11:
	private static final String MISSING_PRODUCED_BY = "/missing_produced_by_941.t2flow";
	
	@Test
	public void readSimpleWorkflow() throws Exception {
		URL wfResource = getClass().getResource(AS_T2FLOW);
		assertNotNull("Could not find workflow " + AS_T2FLOW, wfResource);
		T2FlowParser parser = new T2FlowParser();
		parser.setStrict(true);
		WorkflowBundle wfBundle = parser.parseT2Flow(wfResource.openStream());
		Profile profile = wfBundle.getMainProfile();
		assertEquals(1, wfBundle.getProfiles().size());
		assertEquals(profile, wfBundle.getProfiles().getByName("taverna-2.1.0"));
		
	}
	
	@Test
	public void unconfigureLoopLayer() throws Exception {
		URL wfResource = getClass().getResource(INTERACTION_WITH_LOOP);
		assertNotNull("Could not find workflow " + INTERACTION_WITH_LOOP, wfResource);
		T2FlowParser parser = new T2FlowParser();
		parser.setStrict(false);
		WorkflowBundle wfBundle = parser.parseT2Flow(wfResource.openStream());
		Scufl2Tools scufl2Tools = new Scufl2Tools();
		Processor interaction = wfBundle.getMainWorkflow().getProcessors().getByName("BioSTIFInteraction");
		
		Configuration config = scufl2Tools.configurationFor(interaction, wfBundle.getMainProfile());
		assertNull(config.getJsonAsObjectNode().get("loop"));
	}
	
	@Test
	public void parseMissingProducedBy() throws Exception {
		URL wfResource = getClass().getResource(MISSING_PRODUCED_BY);
		assertNotNull("Could not find workflow " + MISSING_PRODUCED_BY, wfResource);
		T2FlowParser parser = new T2FlowParser();
		WorkflowBundle wfBundle = parser.parseT2Flow(wfResource.openStream());
		Profile profile = wfBundle.getMainProfile();
		assertEquals("unspecified", profile.getName());		
	}
	
}