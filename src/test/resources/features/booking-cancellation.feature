@US-12
Feature: Booking cancellation

  Scenario: Cancel a requested booking successfully
    Given a requested booking exists for passenger "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    When the passenger cancels the booking
    Then the booking status is "CANCELLED"

  Scenario: Reject cancellation of a completed booking
    Given a completed booking exists for passenger "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    When the passenger cancels the booking
    Then cancellation is rejected with conflict

  Scenario: Cancel twice remains idempotent
    Given a requested booking exists for passenger "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    And the passenger cancels the booking
    When the passenger cancels the booking again
    Then the booking status remains "CANCELLED"
