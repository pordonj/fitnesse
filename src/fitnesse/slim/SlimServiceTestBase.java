// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.slim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static util.ListUtility.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// Extracted Test class to be implemented by all Java based Slim ports
// The tests for PhpSlim and JsSlim implement this class

public abstract class SlimServiceTestBase {
  protected List<Object> statements;
  protected SlimClient slimClient;

  protected abstract void startSlimService() throws Exception;

  protected abstract String getImport();

  protected abstract String expectedExceptionMessage();

  protected abstract String expectedStopTestExceptionMessage();

  @Before
  public void setUp() throws Exception {
    createSlimService();
    slimClient = new SlimClient("localhost", 8099);
    statements = new ArrayList<Object>();
    slimClient.connect();
  }

  protected void createSlimService() throws Exception {
    while (!tryCreateSlimService())
      Thread.sleep(10);
  }

  private boolean tryCreateSlimService() throws Exception {
    try {
      startSlimService();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @After
  public void after() throws Exception {
    teardown();
  }

  protected void teardown() throws Exception {
    slimClient.sendBye();
    slimClient.close();
  }

  @Test
  public void emptySession() throws Exception {
    assertTrue("Connected", slimClient.isConnected());
  }

  @Test
  public void versionNumberShouldBeDetected() throws Exception {
    double slimVersion = Double.parseDouble(SlimVersion.VERSION);
    assertEquals(slimVersion, slimClient.getServerVersion(), .0001);
  }

  @Test
  public void callOneMethod() throws Exception {
    addImportAndMake();
    addEchoInt("id", "1");
    Map<String, Object> result = slimClient.invokeAndGetResponse(statements);
    assertEquals("1", result.get("id"));
  }

  private void addEchoInt(String id, String number) {
    statements.add(list(id, "call", "testSlim", "echoInt", number));
  }

  private void addImportAndMake() {
    statements.add(list("i1", "import", getImport()));
    statements.add(list("m1", "make", "testSlim", "TestSlim"));
  }

  @Test
  public void makeManyCallsInOrderToTestLongSequencesOfInstructions() throws Exception {
    addImportAndMake();
    for (int i = 0; i < 1000; i++)
      addEchoInt(String.format("id_%d", i), Integer.toString(i));
    Map<String, Object> result = slimClient.invokeAndGetResponse(statements);
    for (int i = 0; i < 1000; i++)
      assertEquals(i, Integer.parseInt((String) result.get(String.format("id_%d", i))));
  }

  @Test
  public void callWithLineBreakInStringArgument() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "testSlim", "echoString", "hello\nworld\n"));
    Map<String, Object> result = slimClient.invokeAndGetResponse(statements);
    assertEquals("hello\nworld\n", result.get("id"));
  }

  @Test
  public void callWithMultiByteChar() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "testSlim", "echoString", "K\u00f6ln"));
    Map<String, Object> result = slimClient.invokeAndGetResponse(statements);
    assertEquals("K\u00f6ln", result.get("id"));
  }

  @Test
  public void makeManyIndividualCalls() throws Exception {
    addImportAndMake();
    slimClient.invokeAndGetResponse(statements);
    for (int i = 0; i < 100; i++) {
      statements.clear();
      addEchoInt("id", "42");
      Map<String, Object> result = slimClient.invokeAndGetResponse(statements);
      assertEquals(1, result.size());
      assertEquals("42", result.get("id"));
    }
  }

  @Test
  public void callFunctionThatDoesntExist() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "testSlim", "noSuchFunction"));
    Map<String, Object> results = slimClient.invokeAndGetResponse(statements);
    assertContainsException("message:<<NO_METHOD_IN_CLASS", "id", results);
  }

  private void assertContainsException(String message, String id, Map<String, Object> results) {
    String result = (String) results.get(id);
    assertTrue(result, result.contains(SlimServer.EXCEPTION_TAG)
        && result.contains(message));
  }

  @Test
  public void makeClassThatDoesntExist() throws Exception {
    statements.add(list("m1", "make", "me", "NoSuchClass"));
    Map<String, Object> results = slimClient.invokeAndGetResponse(statements);
    assertContainsException("message:<<COULD_NOT_INVOKE_CONSTRUCTOR", "m1", results);
  }

  @Test
  public void useInstanceThatDoesntExist() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "noInstance", "f"));
    Map<String, Object> results = slimClient.invokeAndGetResponse(statements);
    assertContainsException("message:<<NO_INSTANCE", "id", results);
  }

  @Test
  public void verboseArgument() throws Exception {
    String args[] = {"-v", "99"};
    assertTrue(SlimService.parseCommandLine(args));
    assertTrue(SlimService.verbose);
  }

  @Test
  public void notStopTestExceptionThrown() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "testSlim", "throwNormal"));
    statements.add(list("id2", "call", "testSlim", "throwNormal"));
    Map<String, Object> results = slimClient.invokeAndGetResponse(statements);
    assertContainsException("__EXCEPTION__:" + expectedExceptionMessage(), "id", results);
    assertContainsException("__EXCEPTION__:" + expectedExceptionMessage(), "id2", results);
  }

  @Test
  public void stopTestExceptionThrown() throws Exception {
    addImportAndMake();
    statements.add(list("id", "call", "testSlim", "throwStopping"));
    statements.add(list("id2", "call", "testSlim", "throwNormal"));
    Map<String, Object> results = slimClient.invokeAndGetResponse(statements);
    assertContainsException("__EXCEPTION__:" + expectedStopTestExceptionMessage(), "id", results);
    assertNull(results.get("id2"));
  }

  @Test
  public void canSpecifyAnInteractionClass() {
    SlimService.parseCommandLine(new String[]{"-i", "fitnesse.slim.fixtureInteraction.DefaultInteraction"});
    assertEquals("fitnesse.slim.fixtureInteraction.DefaultInteraction", SlimService.getInteractionClass().getName());
  }

  @Test
  public void canSpecifyAComplexCommandLine() {
    String commandLine = "-v -i fitnesse.slim.fixtureInteraction.DefaultInteraction 7890";
    String[] args = commandLine.split(" ");

    assertTrue("should parse correctly", SlimService.parseCommandLine(args));
    assertEquals("should have interaction class set", "fitnesse.slim.fixtureInteraction.DefaultInteraction", SlimService.getInteractionClass().getName());
    assertTrue("should be verbose", SlimService.verbose);
    assertEquals("should have set port", 7890, SlimService.port);
  }

  @Test
  public void canSpecifyComplexArgs() {
    String commandLine = "-v -i fitnesse.slim.fixtureInteraction.DefaultInteraction 7890";
    String[] args = commandLine.split(" ");

    assertTrue("should parse correctly", SlimService.parseCommandLine(args));
    assertEquals("should have interaction class set", "fitnesse.slim.fixtureInteraction.DefaultInteraction", SlimService.getInteractionClass().getName());
    assertTrue("should be verbose", SlimService.verbose);
    assertEquals("should have set port", 7890, SlimService.port);
  }

}
