package org.jspec.runner;

import static java.util.Collections.synchronizedList;
import static org.hamcrest.Matchers.*;
import static org.jspec.util.Assertions.assertListEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspec.proto.JSpecExamples;
import org.jspec.util.RunListenerSpy;
import org.jspec.util.RunListenerSpy.Event;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import com.google.common.collect.ImmutableList;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class NewJSpecRunnerTest {
  public class constructor {
    @Test
    public void givenAConfigurationWithoutErrors_raisesNoError() {
      runnerFor(configOf(JSpecExamples.One.class));
    }

    @Test
    public void givenAConfigurationWith1OrMoreErrors_raisesInitializationErrorWithThoseErrors() {
      TestConfiguration config = configFinding(new IllegalArgumentException(), new AssertionError());
      assertInitializationError(config, ImmutableList.of(IllegalArgumentException.class, AssertionError.class));
    }
    
    @Test
    public void givenAContextClassSuitableForJSpecButNotForJUnit_raisesNoError() {
      runnerFor(JSpecExamples.MultiplePublicConstructors.class);
    }

    private void assertInitializationError(TestConfiguration config, List<Class<? extends Throwable>> expectedCauses) {
      try {
        new NewJSpecRunner(config);
      } catch (InitializationError ex) {
        assertListEquals(expectedCauses, flattenCauses(ex).map(Throwable::getClass).collect(Collectors.toList()));
        return;
      }
      fail(String.format("Expected causes of initialization error to be <%s>, but nothing was thrown", expectedCauses));
    }
  }
  
  public class getDescription {
    public class givenATestConfigurationOrContextClass {
      @Test
      public void describesTheConfiguredClass() {
        assertDescribesIgnoredClass(runnerFor(JSpecExamples.IgnoredClass.class));
        assertDescribesIgnoredClass(runnerFor(configOf(JSpecExamples.IgnoredClass.class)));
      }
      
      private void assertDescribesIgnoredClass(Runner runner) {
        Description description = runner.getDescription();
        assertThat(description.getTestClass(), equalTo(JSpecExamples.IgnoredClass.class));
        assertThat(description.getAnnotation(Ignore.class), notNullValue());
      }
    }
  
    public class givenATestConfigurationWith1OrMoreExamples {
      private final Description subject;
      
      public givenATestConfigurationWith1OrMoreExamples() throws ReflectiveOperationException {
        Class<?> context = JSpecExamples.Two.class;
        Runner runner = runnerFor(configOf(context, exampleNamed("one"), exampleNamed("another")));
        this.subject = runner.getDescription();
      }
      
      @Test
      public void hasAChildDescriptionForEachExample() {
        ArrayList<Description> children = subject.getChildren();
        assertListEquals(
          ImmutableList.of(JSpecExamples.Two.class, JSpecExamples.Two.class),
          children.stream().map(Description::getTestClass).collect(Collectors.toList()));
        assertListEquals(
          ImmutableList.of("one(org.jspec.proto.JSpecExamples$Two)", "another(org.jspec.proto.JSpecExamples$Two)"), 
          children.stream().map(Description::getDisplayName).collect(Collectors.toList()));
      }
    }
  }
  
  public class run {
    private final List<Event> events = synchronizedList(new LinkedList<Event>());
    
    public class givenAContextClassWith1OrMoreExamples {
      public class whenTheExampleThrowsNoException {
        private final Class<?> context = JSpecExamples.One.class;
        
        @Before
        public void setup() throws Exception {
          Runner runner = runnerFor(configOf(context, exampleSpy("only_test", events::add)));
          runTests(runner);
        }

        @Test
        public void notifiesStartRunsTheExampleThenNotifiesFinish() {
          assertThat(events.stream().map(x -> x.name).collect(Collectors.toList()), 
            contains(is("testStarted"), is("run"), is("testFinished")));
        }
      }
    }
    
    private void runTests(Runner runner) {
      RunNotifier notifier = new RunNotifier();
      notifier.addListener(new RunListenerSpy(events::add));
      runner.run(notifier);
    }
  }
  
  private static TestConfiguration configOf(Class<?> contextClass, Example... examples) {
    return new TestConfiguration() {
      @Override
      public List<Throwable> findInitializationErrors() { return Collections.emptyList(); }

      @Override
      public boolean hasInitializationErrors() { return false; }
      
      @Override
      public Class<?> getContextClass() { return contextClass; }

      @Override
      public List<Example> getExamples() { return Arrays.asList(examples); }
    };
  }
  
  private static TestConfiguration configFinding(Throwable... errors) {
    return new TestConfiguration() {
      @Override
      public List<Throwable> findInitializationErrors() { return Arrays.asList(errors); }

      @Override
      public boolean hasInitializationErrors() { return errors.length > 0; }

      @Override
      public Class<?> getContextClass() { return JSpecExamples.One.class; }

      @Override
      public List<Example> getExamples() { 
        String msg = String.format("This configuration is invalid, finding %s", findInitializationErrors());
        throw new IllegalStateException(msg);
      }
    };
  }
  
  private static Example exampleNamed(String behaviorName) {
    return new Example() {
      @Override
      public String describeBehavior() { return behaviorName; }
      
      @Override
      public void run(Object objectDeclaringBehavior) throws Exception { return; }
    };
  }
    
  private static Example exampleSpy(String behaviorName, Consumer<RunListenerSpy.Event> notify) {
    return new Example() {
      @Override
      public String describeBehavior() { return behaviorName; }

      @Override
      public void run(Object objectDeclaringBehavior) throws Exception { 
        notify.accept(new RunListenerSpy.Event("run", null));
      }
    };
  }
  
  private static NewJSpecRunner runnerFor(Class<?> contextClass) {
    try {
      return new NewJSpecRunner(contextClass);
    } catch (InitializationError e) {
      return failForInitializationError(e);
    }
  }
  
  private static NewJSpecRunner runnerFor(TestConfiguration config) {
    try {
      return new NewJSpecRunner(config);
    } catch (InitializationError e) {
      return failForInitializationError(e);
    }
  }
  
  private static NewJSpecRunner failForInitializationError(InitializationError e) {
    System.out.println("\nInitialization error(s)");
    flattenCauses(e).forEach(x -> {
      System.out.printf("[%s]\n", x.getClass());
      x.printStackTrace(System.out);
    });
    fail("Failed to create JSpecRunner");
    return null;
  }
  
  private static Stream<Throwable> flattenCauses(InitializationError root) {
    List<Throwable> causes = new LinkedList<Throwable>();
    Stack<InitializationError> nodesWithChildren = new Stack<InitializationError>();
    nodesWithChildren.push(root);
    while (!nodesWithChildren.isEmpty()) {
      InitializationError parent = nodesWithChildren.pop();
      for(Throwable child : parent.getCauses()) {
        if(child instanceof InitializationError) {
          nodesWithChildren.push((InitializationError) child);
        } else {
          causes.add(child);
        }
      }
    }
    return causes.stream();
  }
}