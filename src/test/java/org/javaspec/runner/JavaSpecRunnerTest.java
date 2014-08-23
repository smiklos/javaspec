package org.javaspec.runner;

import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.javaspec.testutil.Assertions.assertListEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.javaspec.proto.ContextClasses;
import org.javaspec.testutil.RunListenerSpy.Event;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class JavaSpecRunnerTest {
  public class constructor {
    @Test
    public void givenAContextClassSuitableForJavaSpecButNotForJUnit_raisesNoError() {
      Runners.of(ContextClasses.TwoConstructors.class);
    }

    @Test
    public void givenAGatewayWith1OrMoreErrors_raisesInitializationErrorWithThoseErrors() {
      ExampleGateway gateway = gatewayFinding(new IllegalArgumentException(), new AssertionError());
      assertListEquals(Runners.initializationErrorCauses(gateway).stream().map(Throwable::getClass).collect(toList()),
        ImmutableList.of(IllegalArgumentException.class, AssertionError.class));
    }
  }
  
  public class getDescription {
    public class givenAGatewayWith1OrMoreExamples {
      @Test
      public void hasAnnotationsFromEachContextClass() {
        Description description = Runners.of(ContextClasses.IgnoreClass.class).getDescription();
        assertThat(description.getAnnotation(Ignore.class), notNullValue());
      }
      
      @Test
      public void describesEachContextClass() {
        ExampleGateway gateway = Mockito.mock(ExampleGateway.class);
        when(gateway.getContextRoot()).thenReturn(
          contextOf(ContextClasses.NestedIt.class, ContextClasses.NestedIt.innerContext.class));
        
        Description description = Runners.of(gateway).getDescription();
        assertThat(description.getTestClass(), equalTo(ContextClasses.NestedIt.class));
        assertThat(description.getChildren(), hasSize(1));
        assertThat(description.getChildren().get(0).getTestClass(), equalTo(ContextClasses.NestedIt.innerContext.class));
      }
      
      @Test
      public void hasAChildDescriptionForEachExample() {
        Runner runner = Runners.of(gatewayFor(ContextClasses.TwoIt.class, exampleNamed("one"), exampleNamed("another")));
        Description subject = runner.getDescription();
        assertListEquals(
          ImmutableList.of("one(org.javaspec.proto.ContextClasses$TwoIt)", "another(org.javaspec.proto.ContextClasses$TwoIt)"), 
          subject.getChildren().stream().map(Description::getDisplayName).collect(toList()));
      }
    }
    
    private Context contextOf(Class<?> parent, Class<?> child) {
      Context root = new Context(parent);
      root.addChild(child);
      return root;
    }
  }
  
  public class run {
    private final List<Event> events = synchronizedList(new LinkedList<Event>());
    private final Class<?> context = ContextClasses.OneIt.class;
    
    public class givenASkippedExample {
      private final Example skipped = exampleSkipped();
      
      @Before
      public void setup() throws Exception {
        Runner runner = Runners.of(gatewayFor(context, skipped));
        Runners.runAll(runner, events::add);
      }

      @Test
      public void doesNotRunTheExample() throws Exception {
        verify(skipped, never()).run();
      }
      
      @Test
      public void notifiesTestIgnored() {
        assertThat(events.stream().map(Event::getName).collect(toList()),
          contains(equalTo("testIgnored")));
      }
    }
    
    public class givenAPassingExample {
      @Before
      public void setup() throws Exception {
        Runner runner = Runners.of(gatewayFor(context, exampleSpy("passing", events::add)));
        Runners.runAll(runner, events::add);
      }
      
      @Test
      public void runsBetweenNotifyStartAndFinish() {
        assertListEquals(ImmutableList.of("testStarted", "run::passing", "testFinished"),
          events.stream().map(Event::getName).collect(toList()));
        assertThat(events.stream().map(Event::describedDisplayName).collect(toList()), 
          contains(Matchers.startsWith("passing"), anything(), Matchers.startsWith("passing")));
      }
    }
    
    public class givenAFailingExample {
      @Before
      public void setup() throws Exception {
        Runner runner = Runners.of(gatewayFor(context, exampleFailing("boom"), exampleSpy("successor", events::add)));
        Runners.runAll(runner, events::add);
      }
      
      @Test
      public void notifiesTestFailed() {
        assertThat(events.stream().map(Event::getName).collect(toList()), hasItem(equalTo("testFailure")));
        assertThat(events.stream().map(x -> x.failure).collect(toList()), hasItem(notNullValue()));
      }
      
      @Test
      public void continuesRunningSuccessiveTests() {
        assertThat(events.stream().map(Event::getName).collect(toList()), contains(
          "testStarted", "testFailure", "testFinished",
          "testStarted", "run::successor", "testFinished"));
      }
    }
  }
  
  private static ExampleGateway gatewayFor(Class<?> contextClass, Example... examples) {
    return new ExampleGateway() {
      @Override
      public List<Throwable> findInitializationErrors() { return Collections.emptyList(); }
      
      @Override
      public Class<?> getContextClass() { return contextClass; }

      @Override
      public Context getContextRoot() { return new Context(contextClass); }
      
      @Override
      public Stream<Example> getExamples() { return Stream.of(examples); }

      @Override
      public List<String> getExampleNames(Context context) {
        return Stream.of(examples).map(Example::describeBehavior).collect(toList()); 
      }
    };
  }
  
  private static ExampleGateway gatewayFinding(Throwable... errors) {
    ExampleGateway stub = mock(ExampleGateway.class);
    stub(stub.findInitializationErrors()).toReturn(Arrays.asList(errors));
    doThrow(new UnsupportedOperationException("invalid context class")).when(stub).getExamples();
    return stub;
  }

  private static Example exampleFailing(String behaviorName) throws Exception {
    Example stub = exampleNamed(behaviorName);
    doThrow(new AssertionError("bang!")).when(stub).run();
    return stub;
  }
  
  private static Example exampleSkipped() {
    Example stub = exampleNamed("skipper");
    stub(stub.isSkipped()).toReturn(true);
    return stub;
  }
  
  private static Example exampleNamed(String behaviorName) {
    Example stub = mock(Example.class);
    stub(stub.describeBehavior()).toReturn(behaviorName);
    return stub;
  }
  
  private static Example exampleSpy(String behaviorName, Consumer<Event> notify) {
    return new Example() {
      @Override
      public String describeSetup() { return ""; }
      
      @Override
      public String describeAction() { return ""; }
      
      @Override
      public String describeBehavior() { return behaviorName; }

      @Override
      public String describeCleanup() { return ""; }
      
      @Override
      public boolean isSkipped() { return false; }
      
      @Override
      public void run() { notify.accept(Event.named("run::" + behaviorName)); }
    };
  }
}