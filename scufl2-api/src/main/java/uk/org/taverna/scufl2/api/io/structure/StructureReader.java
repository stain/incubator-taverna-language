package uk.org.taverna.scufl2.api.io.structure;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.org.taverna.scufl2.api.activity.Activity;
import uk.org.taverna.scufl2.api.configurations.Configuration;
import uk.org.taverna.scufl2.api.container.WorkflowBundle;
import uk.org.taverna.scufl2.api.core.BlockingControlLink;
import uk.org.taverna.scufl2.api.core.DataLink;
import uk.org.taverna.scufl2.api.core.Processor;
import uk.org.taverna.scufl2.api.core.Workflow;
import uk.org.taverna.scufl2.api.io.ReaderException;
import uk.org.taverna.scufl2.api.io.WorkflowBundleReader;
import uk.org.taverna.scufl2.api.port.InputActivityPort;
import uk.org.taverna.scufl2.api.port.InputProcessorPort;
import uk.org.taverna.scufl2.api.port.InputWorkflowPort;
import uk.org.taverna.scufl2.api.port.OutputActivityPort;
import uk.org.taverna.scufl2.api.port.OutputProcessorPort;
import uk.org.taverna.scufl2.api.port.OutputWorkflowPort;
import uk.org.taverna.scufl2.api.port.ReceiverPort;
import uk.org.taverna.scufl2.api.port.SenderPort;
import uk.org.taverna.scufl2.api.profiles.ProcessorBinding;
import uk.org.taverna.scufl2.api.profiles.ProcessorInputPortBinding;
import uk.org.taverna.scufl2.api.profiles.ProcessorOutputPortBinding;
import uk.org.taverna.scufl2.api.profiles.Profile;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A <code>WorkflowBundleReader</code> that reads a {@link WorkflowBundle} in Scufl2 Structure
 * format.
 */
public class StructureReader implements WorkflowBundleReader {

	public enum Level {
		WorkflowBundle, Workflow, Processor, Activity, Links, Profile, Configuration, ProcessorBinding, OutputPortBindings, InputPortBindings, JSON, Controls

	}

	private static final String ACTIVITY_SLASH = "activity/";

	public static final String TEXT_VND_TAVERNA_SCUFL2_STRUCTURE = "text/vnd.taverna.scufl2.structure";

	private WorkflowBundle wb;

	private Level level;

	private String mainWorkflow;

	private Workflow workflow;

	private Processor processor;

	private Activity activity;

	Pattern linkPattern = Pattern
	.compile("'(.*[^\\\\])'\\s->\\s'(.*[^\\\\\\\\])'");

	Pattern blockPattern = Pattern
	.compile("\\s*block\\s+'(.*[^\\\\])'\\s+until\\s+'(.*[^\\\\\\\\])'\\s+finish");

	private String mainProfile;

	private Profile profile;

	private Configuration configuration;

	private ProcessorBinding processorBinding;

	private StringBuffer propertyString;

    protected Scanner scanner;

	@Override
	public Set<String> getMediaTypes() {
		return Collections.singleton(TEXT_VND_TAVERNA_SCUFL2_STRUCTURE);
	}

	protected synchronized WorkflowBundle parse(InputStream is) throws ReaderException {

	    // synchronized because we share wb/scanner fields across the instance
	    
		wb = new WorkflowBundle();
		scanner = new Scanner(is);
		try {
			while (scanner.hasNextLine()) {
				parseLine(scanner.nextLine());
			}
		} finally {
			scanner.close();
		}

		return wb;
	}

	protected void parseLine(final String nextLine) throws ReaderException {
		Scanner scanner = new Scanner(nextLine.trim());
		// In case it is the last line
		if (! scanner.hasNext()) {
		    return;
		}
		// allow any whitespace
		String next = scanner.next();

		if (next.isEmpty()) {
			return;
		}
		if (next.equals("WorkflowBundle")) {
			level = Level.WorkflowBundle;
			String name = parseName(scanner);
			wb.setName(name);
			return;
		}
		if (next.equals("MainWorkflow")) {
			mainWorkflow = parseName(scanner);
			return;
		}
		if (next.equals("Workflow")) {
			level = Level.Workflow;
			workflow = new Workflow();
			String workflowName = parseName(scanner);
			workflow.setName(workflowName);
			wb.getWorkflows().add(workflow);
			if (workflowName.equals(mainWorkflow)) {
				wb.setMainWorkflow(workflow);
			}
			return;
		}
		if (next.equals("In") || next.equals("Out")) {
			boolean in = next.equals("In");
			String portName = parseName(scanner);
			switch (level) {
				case Workflow:
					if (in) {
						new InputWorkflowPort(workflow, portName);
					} else {
						new OutputWorkflowPort(workflow, portName);
					}
					break;
				case Processor:
					if (in) {
						new InputProcessorPort(processor, portName);
					} else {
						new OutputProcessorPort(processor, portName);
					}
					break;
				case Activity:
					if (in) {
						new InputActivityPort(activity, portName);
					} else {
						new OutputActivityPort(activity, portName);
					}
					break;
				default:
					throw new ReaderException("Unexpected " + next + " at level "
							+ level);
			}
			return;
		}
		if (next.equals("Processor")
				&& (level == Level.Workflow || level == Level.Processor)) {
			level = Level.Processor;
			processor = new Processor();
			String processorName = parseName(scanner);
			processor.setName(processorName);
			processor.setParent(workflow);
			workflow.getProcessors().add(processor);
			return;
		}
		if (next.equals("Links")) {
			level = Level.Links;
			processor = null;
			return;
		}
		if (next.equals("Controls")) {
			level = Level.Controls;
			return;
		}
		if (next.equals("block")) {
			Matcher blockMatcher = blockPattern.matcher(nextLine);
			blockMatcher.find();
			String block = blockMatcher.group(1);
			String untilFinish = blockMatcher.group(2);

			Processor blockProc = workflow.getProcessors().getByName(block);
			Processor untilFinishedProc = workflow.getProcessors().getByName(
					untilFinish);
			new BlockingControlLink(blockProc, untilFinishedProc);
		}
		if (next.startsWith("'") && level.equals(Level.Links)) {
			Matcher linkMatcher = linkPattern.matcher(nextLine);
			linkMatcher.find();
			String firstLink = linkMatcher.group(1);
			String secondLink = linkMatcher.group(2);

			SenderPort senderPort;
			if (firstLink.contains(":")) {
				String[] procPort = firstLink.split(":");
				Processor proc = workflow.getProcessors()
				.getByName(procPort[0]);
				senderPort = proc.getOutputPorts().getByName(procPort[1]);
			} else {
				senderPort = workflow.getInputPorts().getByName(firstLink);
			}

			ReceiverPort receiverPort;
			if (secondLink.contains(":")) {
				String[] procPort = secondLink.split(":");
				Processor proc = workflow.getProcessors()
				.getByName(procPort[0]);
				receiverPort = proc.getInputPorts().getByName(procPort[1]);
			} else {
				receiverPort = workflow.getOutputPorts().getByName(secondLink);
			}

			new DataLink(workflow, senderPort, receiverPort);
			return;
		}

		if (next.equals("MainProfile")) {
			mainProfile = parseName(scanner);
			return;
		}
		if (next.equals("Profile")) {
			level = Level.Profile;
			profile = new Profile();
			String profileName = parseName(scanner);
			profile.setName(profileName);
			wb.getProfiles().add(profile);
			if (profileName.equals(mainProfile)) {
				wb.setMainProfile(profile);
			}
			return;
		}
		if (next.equals("Activity")
				&& (level == Level.Profile || level == Level.Activity)) {
			level = Level.Activity;
			activity = new Activity();
			String activityName = parseName(scanner);
			activity.setName(activityName);
			profile.getActivities().add(activity);
			return;
		}
		if (next.equals("Type")) {
			URI uri = URI.create(nextLine.split("[<>]")[1]);
			if (level == Level.Activity) {
				activity.setType(uri);
			} else if (level == Level.Configuration) {
				configuration.setType(uri);
			}
			return;
		}
		if (next.equals("ProcessorBinding")) {
			level = Level.ProcessorBinding;
			processorBinding = new ProcessorBinding();
			String bindingName = parseName(scanner);
			processorBinding.setName(bindingName);
			profile.getProcessorBindings().add(processorBinding);
			return;
		}
		if (next.equals("Activity") && level == Level.ProcessorBinding) {
			String activityName = parseName(scanner);
			Activity boundActivity = profile.getActivities().getByName(
					activityName);
			processorBinding.setBoundActivity(boundActivity);
			return;
		}
		if (next.equals("Processor") && level == Level.ProcessorBinding) {
			String[] wfProcName = parseName(scanner).split(":");
			Workflow wf = wb.getWorkflows().getByName(wfProcName[0]);
			Processor boundProcessor = wf.getProcessors().getByName(
					wfProcName[1]);
			processorBinding.setBoundProcessor(boundProcessor);
			return;
		}
		if (next.equals("InputPortBindings")) {
			level = Level.InputPortBindings;
			return;
		}
		if (next.equals("OutputPortBindings")) {
			level = Level.OutputPortBindings;
			return;
		}

		if (next.startsWith("'")
				&& (level == Level.InputPortBindings || level == Level.OutputPortBindings)) {
			Matcher linkMatcher = linkPattern.matcher(nextLine);
			linkMatcher.find();
			String firstLink = linkMatcher.group(1);
			String secondLink = linkMatcher.group(2);
			if (level == Level.InputPortBindings) {
				InputProcessorPort processorPort = processorBinding
				.getBoundProcessor().getInputPorts()
				.getByName(firstLink);
				InputActivityPort activityPort = processorBinding
				.getBoundActivity().getInputPorts()
				.getByName(secondLink);
				new ProcessorInputPortBinding(processorBinding, processorPort,
						activityPort);
			} else {
				OutputActivityPort activityPort = processorBinding
				.getBoundActivity().getOutputPorts()
				.getByName(firstLink);
				OutputProcessorPort processorPort = processorBinding
				.getBoundProcessor().getOutputPorts()
				.getByName(secondLink);
				new ProcessorOutputPortBinding(processorBinding, activityPort,
						processorPort);
			}
			return;
		}
		if (next.equals("Configuration")) {
			level = Level.Configuration;
			configuration = new Configuration();
			String configName = parseName(scanner);
			configuration.setName(configName);
			profile.getConfigurations().add(configuration);
			return;
		}
		if (next.equals("Configures")) {
			String configures = parseName(scanner);
			if (configures.startsWith(ACTIVITY_SLASH)) {
				String activityName = configures.substring(
						ACTIVITY_SLASH.length(), configures.length());
				activity = profile.getActivities().getByName(activityName);
				configuration.setConfigures(activity);
				level = Level.JSON;
				return;
			} else {
				throw new UnsupportedOperationException("Unknown Configures "
						+ configures);
			}
		}
        if (level == Level.JSON) {

            // A silly reader that feeds (no more than) a single line
            // at a time from our parent scanner, starting with the current line
            Reader reader = new Reader() {
                char[] line = nextLine.toCharArray();
                int pos = 0;

                @Override
                public int read(char[] cbuf, int off, int len)
                        throws IOException {
                    if (pos >= line.length) {
                        // Need to read next line to fill buffer
                        if (! StructureReader.this.scanner.hasNextLine()) {
                            return -1;
                        }
                        String newLine = StructureReader.this.scanner.nextLine();
                        pos = 0;
                        line = newLine.toCharArray();
//                        System.out.println("Read new line: " + newLine);
                    }
                    int length = Math.min(len, line.length - pos);
                    if (length <= 0) {
                        return 0;
                    }
                    System.arraycopy(line, pos, cbuf, off, length);
                    pos += length;
                    return length;
                }

                @Override
                public void close() throws IOException {
                    line = null;
                }
            };
            
            
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonParser parser = mapper.getFactory().createParser(reader);
                JsonNode jsonNode = parser.readValueAs(JsonNode.class);
//                System.out.println("Parsed " + jsonNode);
                configuration.setJson(jsonNode);
            } catch (JsonParseException e) {
                throw new ReaderException("Can't parse json", e);
            } catch (IOException e) {
                throw new ReaderException("Can't parse json", e);
            }
            level = Level.Configuration;
            return;
        }
    }


	private String parseName(Scanner scanner) {
		String name = scanner.findInLine("'(.*[^\\\\])'");
		return name.substring(1, name.length() - 1);
	}

	@Override
	public WorkflowBundle readBundle(File bundleFile, String mediaType)
	throws IOException, ReaderException {
		BufferedInputStream is = new BufferedInputStream(new FileInputStream(
				bundleFile));
		try {
			return parse(is);
		} finally {
			is.close();
		}
	}

	@Override
 	public WorkflowBundle readBundle(InputStream inputStream, String mediaType)
	throws IOException, ReaderException {
		return parse(inputStream);

	}

	@Override
	public String guessMediaTypeForSignature(byte[] firstBytes) {
		if (new String(firstBytes, Charset.forName("ISO-8859-1")).contains("WorkflowBundle '")) {
			return TEXT_VND_TAVERNA_SCUFL2_STRUCTURE;
		}
		return null;
	}

}
