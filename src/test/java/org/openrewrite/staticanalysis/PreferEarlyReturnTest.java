/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class PreferEarlyReturnTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferEarlyReturn());
    }

    @DocumentExample
    @Test
    void basicIfElseWithEarlyReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processOrder(Order order) {
                      if (order != null && order.isValid()) {
                          // Process the order
                          order.validate();
                          order.calculateTax();
                          order.applyDiscount();
                          order.processPayment();
                          order.sendConfirmation();
                      } else {
                          logError("Invalid order");
                          return;
                      }
                  }

                  void logError(String message) {}

                  interface Order {
                      boolean isValid();
                      void validate();
                      void calculateTax();
                      void applyDiscount();
                      void processPayment();
                      void sendConfirmation();
                  }
              }
              """,
            """
              class Test {
                  void processOrder(Order order) {
                      if (!(order != null && order.isValid())) {
                          logError("Invalid order");
                          return;
                      }
                      // Process the order
                      order.validate();
                      order.calculateTax();
                      order.applyDiscount();
                      order.processPayment();
                      order.sendConfirmation();
                  }

                  void logError(String message) {}

                  interface Order {
                      boolean isValid();
                      void validate();
                      void calculateTax();
                      void applyDiscount();
                      void processPayment();
                      void sendConfirmation();
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleConditionsWithAndOperator() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean processUser(User user) {
                      if (user != null && user.isActive() && !user.isSuspended()) {
                          // Main processing logic
                          user.updateLastLogin();
                          user.incrementLoginCount();
                          user.loadPreferences();
                          user.initializeSession();
                          user.logActivity();
                          return true;
                      } else {
                          return false;
                      }
                  }

                  interface User {
                      boolean isActive();
                      boolean isSuspended();
                      void updateLastLogin();
                      void incrementLoginCount();
                      void loadPreferences();
                      void initializeSession();
                      void logActivity();
                  }
              }
              """,
            """
              class Test {
                  boolean processUser(User user) {
                      if (!(user != null && user.isActive() && !user.isSuspended())) {
                          return false;
                      }
                      // Main processing logic
                      user.updateLastLogin();
                      user.incrementLoginCount();
                      user.loadPreferences();
                      user.initializeSession();
                      user.logActivity();
                      return true;
                  }

                  interface User {
                      boolean isActive();
                      boolean isSuspended();
                      void updateLastLogin();
                      void incrementLoginCount();
                      void loadPreferences();
                      void initializeSession();
                      void logActivity();
                  }
              }
              """
          )
        );
    }

    @Test
    void methodWithReturnValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String processData(Data data) {
                      if (data != null && data.isValid()) {
                          // Process the data
                          String result = data.transform();
                          result = result.trim();
                          result = result.toUpperCase();
                          data.log(result);
                          return result;
                      } else {
                          return null;
                      }
                  }

                  interface Data {
                      boolean isValid();
                      String transform();
                      void log(String s);
                  }
              }
              """,
            """
              class Test {
                  String processData(Data data) {
                      if (!(data != null && data.isValid())) {
                          return null;
                      }
                      // Process the data
                      String result = data.transform();
                      result = result.trim();
                      result = result.toUpperCase();
                      data.log(result);
                      return result;
                  }

                  interface Data {
                      boolean isValid();
                      String transform();
                      void log(String s);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenIfBlockTooSmall() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processItem(Item item) {
                      if (item != null) {
                          // Too few statements (less than 5)
                          item.process();
                          item.save();
                      } else {
                          return;
                      }
                  }

                  interface Item {
                      void process();
                      void save();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenElseBlockTooLarge() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processRequest(Request request) {
                      if (request != null && request.isValid()) {
                          // Process the request
                          request.validate();
                          request.authorize();
                          request.execute();
                          request.logSuccess();
                          request.notifyClients();
                      } else {
                          // Too many statements in else block (more than 2)
                          logError("Invalid request");
                          notifyAdmin();
                          incrementErrorCounter();
                          return;
                      }
                  }

                  void logError(String message) {}
                  void notifyAdmin() {}
                  void incrementErrorCounter() {}

                  interface Request {
                      boolean isValid();
                      void validate();
                      void authorize();
                      void execute();
                      void logSuccess();
                      void notifyClients();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNoElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processEvent(Event event) {
                      if (event != null && event.isActive()) {
                          // Process the event
                          event.handle();
                          event.dispatch();
                          event.complete();
                          event.cleanup();
                          event.logCompletion();
                      }
                      // No else block, so no early return to add
                  }

                  interface Event {
                      boolean isActive();
                      void handle();
                      void dispatch();
                      void complete();
                      void cleanup();
                      void logCompletion();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentsAndFormatting() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processPayment(Payment payment) {
                      // Check if payment is valid
                      if (payment != null && payment.isAuthorized()) {
                          // Process the payment
                          payment.validate(); // Validate payment details
                          payment.checkFraud(); // Check for fraud
                          payment.deductAmount(); // Deduct from account
                          payment.recordTransaction(); // Record in database
                          payment.sendReceipt(); // Send receipt to customer
                      } else {
                          // Payment is invalid
                          logError("Unauthorized payment");
                          return;
                      }
                  }

                  void logError(String message) {}

                  interface Payment {
                      boolean isAuthorized();
                      void validate();
                      void checkFraud();
                      void deductAmount();
                      void recordTransaction();
                      void sendReceipt();
                  }
              }
              """,
            """
              class Test {
                  void processPayment(Payment payment) {
                      // Check if payment is valid
                      if (!(payment != null && payment.isAuthorized())) {
                          // Payment is invalid
                          logError("Unauthorized payment");
                          return;
                      }
                      // Process the payment
                      payment.validate(); // Validate payment details
                      payment.checkFraud(); // Check for fraud
                      payment.deductAmount(); // Deduct from account
                      payment.recordTransaction(); // Record in database
                      payment.sendReceipt(); // Send receipt to customer
                  }

                  void logError(String message) {}

                  interface Payment {
                      boolean isAuthorized();
                      void validate();
                      void checkFraud();
                      void deductAmount();
                      void recordTransaction();
                      void sendReceipt();
                  }
              }
              """
          )
        );
    }

    @Test
    void complexConditionWithParentheses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void processTransaction(Transaction tx) {
                      if (tx != null && (tx.isValid() || tx.isPending()) && !tx.isExpired()) {
                          // Process transaction
                          tx.authorize();
                          tx.validate();
                          tx.execute();
                          tx.commit();
                          tx.notifyParties();
                      } else {
                          return;
                      }
                  }

                  interface Transaction {
                      boolean isValid();
                      boolean isPending();
                      boolean isExpired();
                      void authorize();
                      void validate();
                      void execute();
                      void commit();
                      void notifyParties();
                  }
              }
              """,
            """
              class Test {
                  void processTransaction(Transaction tx) {
                      if (!(tx != null && (tx.isValid() || tx.isPending()) && !tx.isExpired())) {
                          return;
                      }
                      // Process transaction
                      tx.authorize();
                      tx.validate();
                      tx.execute();
                      tx.commit();
                      tx.notifyParties();
                  }

                  interface Transaction {
                      boolean isValid();
                      boolean isPending();
                      boolean isExpired();
                      void authorize();
                      void validate();
                      void execute();
                      void commit();
                      void notifyParties();
                  }
              }
              """
          )
        );
    }

    @Test
    void methodThrowingExceptionInElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String validateAndProcess(Input input) {
                      if (input != null && input.isValid() && input.hasRequiredFields()) {
                          // Process the input
                          String normalized = input.normalize();
                          String validated = input.validate();
                          String transformed = input.transform();
                          String encrypted = input.encrypt();
                          String result = input.format(normalized, validated, transformed, encrypted);
                          return result;
                      } else {
                          throw new IllegalArgumentException("Invalid input");
                      }
                  }

                  interface Input {
                      boolean isValid();
                      boolean hasRequiredFields();
                      String normalize();
                      String validate();
                      String transform();
                      String encrypt();
                      String format(String... parts);
                  }
              }
              """,
            """
              class Test {
                  String validateAndProcess(Input input) {
                      if (!(input != null && input.isValid() && input.hasRequiredFields())) {
                          throw new IllegalArgumentException("Invalid input");
                      }
                      // Process the input
                      String normalized = input.normalize();
                      String validated = input.validate();
                      String transformed = input.transform();
                      String encrypted = input.encrypt();
                      String result = input.format(normalized, validated, transformed, encrypted);
                      return result;
                  }

                  interface Input {
                      boolean isValid();
                      boolean hasRequiredFields();
                      String normalize();
                      String validate();
                      String transform();
                      String encrypt();
                      String format(String... parts);
                  }
              }
              """
          )
        );
    }
}
