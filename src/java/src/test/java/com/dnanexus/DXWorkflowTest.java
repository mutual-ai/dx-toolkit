// Copyright (C) 2013-2016 DNAnexus, Inc.
//
// This file is part of dx-toolkit (DNAnexus platform client libraries).
//
//   Licensed under the Apache License, Version 2.0 (the "License"); you may
//   not use this file except in compliance with the License. You may obtain a
//   copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
//   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
//   License for the specific language governing permissions and limitations
//   under the License.

package com.dnanexus;

import java.io.IOException;
import java.util.*;
//import java.util.HashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.dnanexus.DXDataObject.DescribeOptions;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

/**
 * Tests for creating workflows.
 */
public class DXWorkflowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DXProject testProject;

    private ObjectNode makeDXLink(DXDataObject dataObject) {
        return DXJSON.getObjectBuilder().put("$dnanexus_link", dataObject.getId()).build();
    }

    @Before
    public void setUp() {
        testProject = DXProject.newProject().setName("DXWorkflowTest").build();
    }

    @After
    public void tearDown() {
        if (testProject != null) {
            testProject.destroy(true);
        }
    }

    // External tests

    @Test
    public void testCreateWorkflowSimple() {
        DXWorkflow a = DXWorkflow.newWorkflow().setProject(testProject).build();

        DXWorkflow.Describe d = a.describe();
        Assert.assertEquals(testProject, d.getProject());
    }

    @Test
    public void testDescribeWithOptions() {
        DXWorkflow a = DXWorkflow.newWorkflow().setProject(testProject).build();

        DXWorkflow.Describe d = a.describe(DescribeOptions.get());
        Assert.assertEquals(testProject, d.getProject());
    }

    @Test
    public void testGetInstance() {
        DXWorkflow workflow = DXWorkflow.getInstance("workflow-000011112222333344445555");
        Assert.assertEquals("workflow-000011112222333344445555", workflow.getId());
        Assert.assertEquals(null, workflow.getProject());

        DXWorkflow workflow2 =
                DXWorkflow.getInstance("workflow-000100020003000400050006",
                        DXProject.getInstance("project-123412341234123412341234"));
        Assert.assertEquals("workflow-000100020003000400050006", workflow2.getId());
        Assert.assertEquals("project-123412341234123412341234", workflow2.getProject().getId());

        try {
            DXWorkflow.getInstance(null);
            Assert.fail("Expected creation without setting ID to fail");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            DXWorkflow.getInstance("workflow-123412341234123412341234", (DXContainer) null);
            Assert.fail("Expected creation without setting project to fail");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            DXWorkflow.getInstance(null, DXProject.getInstance("project-123412341234123412341234"));
            Assert.fail("Expected creation without setting ID to fail");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testDataObjectMethods() {
        DXDataObjectTest.BuilderFactory<DXWorkflow.Builder, DXWorkflow> builderFactory =
                new DXDataObjectTest.BuilderFactory<DXWorkflow.Builder, DXWorkflow>() {
                    @Override
                    public DXWorkflow.Builder getBuilder() {
                        return DXWorkflow.newWorkflow();
                    }
                };

        DXDataObjectTest.testOpenDataObjectMethods(testProject, builderFactory);
        DXDataObjectTest.testClosedDataObjectMethods(testProject, builderFactory);
    }

    @Test
    public void testBuilder() {
        DXDataObjectTest.testBuilder(testProject,
                new DXDataObjectTest.BuilderFactory<DXWorkflow.Builder, DXWorkflow>() {
                    @Override
                    public DXWorkflow.Builder getBuilder() {
                        return DXWorkflow.newWorkflow();
                    }
                });
    }

    @Test
    public void testRunWorkflow() {
        DXWorkflow workflow = DXWorkflow.newWorkflow().setProject(testProject).build();

        // Create an applet to be added to the workflow and some inputs to be supplied to it
        InputParameter inputString = InputParameter.newInputParameter("input_string",
                IOClass.STRING).build();
        InputParameter inputRecord = InputParameter.newInputParameter("input_record",
                IOClass.RECORD).build();
        OutputParameter outputRecord = OutputParameter.newOutputParameter("output_record",
                IOClass.RECORD).build();

        DXRecord myRecord = DXRecord.newRecord().setProject(testProject).setName("myRecord")
                .build().close();

        String code = "dx-jobutil-add-output output_record `dx-jobutil-parse-link \"$input_record\"` --class=record\n";

        DXApplet applet = DXApplet.newApplet().setProject(testProject)
                .setName("applet_for_java_test")
                .setRunSpecification(RunSpecification.newRunSpec("bash", code).build())
                .setInputSpecification(ImmutableList.of(inputString, inputRecord))
                .setOutputSpecification(ImmutableList.of(outputRecord)).build();

        DXStage stage1 = workflow.addStage(applet, "stageA", null, 0);
        DXStage stage2 = workflow.addStage(applet, "stageB", null, stage1.getEditVersion());

        // Supply workflow inputs in the format STAGE.INPUTNAME
        ObjectNode runInput = DXJSON.getObjectBuilder()
            .put(stage1.getId() + ".input_string", "foo")
            .put(stage1.getId() + ".input_record", makeDXLink(myRecord))
            .put(stage2.getId() + ".input_string", "bar")
            .put(stage2.getId() + ".input_record", makeDXLink(myRecord)).build();

        // We run a workflow here, but do not wait for its result, so it's fine that this test
        // doesn't check for ConfigOption.RUN_JOBS.
        DXAnalysis analysis = workflow.newRun().setInput(runInput).setProject(testProject).run();
        analysis.terminate();
    }

    // Internal tests

    /**
     * Tests serialization of the input hash to /workflow/new
     */
    @Test
    public void testCreateWorkflowSerialization() throws IOException {
        Assert.assertEquals(
                DXJSON.parseJson("{\"project\":\"project-000011112222333344445555\", \"name\": \"foo\"}"),
                MAPPER.valueToTree(DXWorkflow.newWorkflow()
                        .setProject(DXProject.getInstance("project-000011112222333344445555"))
                        .setName("foo").buildRequestHash()));
    }

    private static void prettyPrintJsonNode(ObjectNode jnode) {
        try {
            String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jnode);
            System.err.println(pretty);
        } catch (Exception e) {
            System.err.println("Caught exception" + e.getStackTrace());
        }
    }

    //
    // Create a link to a field in a previous stage. The intended use,
    // is to plug that value, once calculated, into the current stage.
    //
    // This method is equivalent to following two example python calls:
    // dxpy.dxlink({'stage': etl_stage_id, 'outputField': 'discovered_alleles'}),
    // dxpy.dxlink({'stage': etl_stage_id, 'inputField': 'bed_ranges_to_discover_alleles'})
    //
    // The expected JSON is something like this:
    //  {u'$dnanexus_link': {'inputField': 'bed_ranges_to_discover_alleles',
    //                       'stage': u'stage-F0b28zQ04YPjPxP1Qj6z9qvJ'}}
    private static ObjectNode makeDXLink(DXStage stage,
                                         boolean input,
                                         String  fieldName) {
        String modifier = null;
        if (input)
            modifier = "inputField";
        else
            modifier = "outputField";
        ObjectNode promise = DXJSON.getObjectBuilder()
            .put("stage", stage.getId())
            .put(modifier, fieldName).build();
        ObjectNode retval = DXJSON.getObjectBuilder().put("$dnanexus_link", promise).build();

        return retval;
    }

    @JsonInclude(Include.NON_NULL)
    private static class OutputVars {
        @JsonProperty
        int sumA;

        @JsonProperty
        int sumB;
    }

    @Test
    public void testRunWorkflowWithDependencies() {
        DXWorkflow workflow = DXWorkflow.newWorkflow().setProject(testProject).build();

        // Create an applet that adds two numbers
        InputParameter inputA = InputParameter.newInputParameter("ai", IOClass.INT).build();
        InputParameter inputB = InputParameter.newInputParameter("bi", IOClass.INT).build();
        OutputParameter outputSum = OutputParameter.newOutputParameter("sum",  IOClass.INT).build();
        String code = "dx-jobutil-add-output sum `echo $((ai + bi))` --class=int\n";
        DXApplet applet = DXApplet.newApplet().setProject(testProject)
            .setName("applet_add_java")
            .setRunSpecification(RunSpecification.newRunSpec("bash", code).build())
            .setInputSpecification(ImmutableList.of(inputA, inputB))
            .setOutputSpecification(ImmutableList.of(outputSum)).build();

        // Stage 1
        DXStage stage1 = workflow.addStage(applet, "stageA", null, 0);

        // Stage 2: waits for the result of the previous stage, and adds another number
        ObjectNode runInput2 = DXJSON.getObjectBuilder()
            .put("ai", makeDXLink(stage1, false, "sum"))
            .put("bi", 4)
            .build();

        DXStage stage2 = workflow.addStage(applet, "stageB", runInput2, stage1.getEditVersion());

        // Supply workflow inputs in the format STAGE.INPUTNAME
        ObjectNode runInput = DXJSON.getObjectBuilder()
            .put(stage1.getId() + ".ai", 1)
            .put(stage1.getId() + ".bi", 2).build();

        // We run a workflow here, but do not wait for its result, so it's fine that this test
        // doesn't check for ConfigOption.RUN_JOBS.
        DXAnalysis analysis = workflow.newRun().setInput(runInput).setProject(testProject).run();
        analysis.waitUntilDone();
        System.err.println("Completed waiting for analysis");

        // The results are supposed to be something like this:
        //{
        //   "stage-F0b4zz807vqPqYzGJbxQ712k.sum" : 3,
        //   "stage-F0b4zzQ07vqFgvfJ5xfjPg16.sum" : 7
        //}
        ObjectNode jnode = analysis.getOutput(ObjectNode.class);
        prettyPrintJsonNode(jnode);

        //Assert.assertEquals("sum", analysis.getOutput(OutputVars.class).outputParam);
        //OutputVars oVars = analysis.getOutput(OutputVars.class);
        //System.err.println("sum=" + oVars.sumA + " " + oVars.sumB);
    }
}
