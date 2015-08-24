package info.javaspec.spec;

import info.javaspec.dsl.Before;
import info.javaspec.dsl.Cleanup;
import info.javaspec.dsl.It;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Field;
import java.util.List;

import static java.util.stream.Collectors.toList;

final class FieldSpec extends Spec {
  private final Description testDescription;
  private final Field assertionField;
  private final List<Field> befores;
  private final List<Field> afters;

  private TestFunction testFunction;

  FieldSpec(String id, Description testDescription, Field it, List<Field> befores, List<Field> afters) {
    super(id);
    this.testDescription = testDescription;
    this.assertionField = it;
    this.befores = befores;
    this.afters = afters;
  }

  @Override
  public Description getDescription() { return testDescription; }

  @Override
  public void addDescriptionTo(Description suite) {
    suite.addChild(testDescription);
  }

  @Override
  public boolean isIgnored() {
    return theTestFunction().hasUnassignedFunctions();
  }

  @Override
  public void run(RunNotifier notifier) {
    notifier.fireTestStarted(getDescription());
    TestFunction f;
    try {
      f = theTestFunction();
    } catch(Exception ex) {
      notifier.fireTestFailure(new Failure(getDescription(), ex));
      return;
    }

    //TODO KDK: Cleanup and test-drive before/after running
    try {
      f.befores.forEach(x -> {
        try {
          x.run();
        } catch(Exception e) {
          e.printStackTrace();
        }
      });
      f.assertion.run();
      f.afters.forEach(x -> {
        try {
          x.run();
        } catch(Exception e) {
          e.printStackTrace();
        }
      });
    } catch(Exception | AssertionError ex) {
      notifier.fireTestFailure(new Failure(getDescription(), ex));
      return;
    }

    notifier.fireTestFinished(getDescription());
  }

  @Override
  public void run() throws Exception {
    TestFunction f = theTestFunction();
    try {
      for(Before before : f.befores) { before.run(); }
      f.assertion.run();
    } finally {
      for(Cleanup after : f.afters) { after.run(); }
    }
  }

  private TestFunction theTestFunction() {
    if(testFunction == null) {
      SpecExecutionContext context = SpecExecutionContext.forDeclaringClass(assertionField.getDeclaringClass());
      try {
        List<Before> beforeValues = befores.stream().map(x -> (Before)context.getAssignedValue(x)).collect(toList());
        List<Cleanup> afterValues = afters.stream().map(x -> (Cleanup)context.getAssignedValue(x)).collect(toList());
        It assertion = (It)context.getAssignedValue(assertionField);
        testFunction = new TestFunction(assertion, beforeValues, afterValues);
      } catch(Throwable t) {
        throw TestSetupFailed.forClass(assertionField.getDeclaringClass(), t);
      }
    }

    return testFunction;
  }
}
