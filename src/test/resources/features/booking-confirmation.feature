@US-04
Feature: Booking confirmation

  Scenario: Confirm a grouped booking successfully
    Given a grouped booking exists for passenger "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    When the proposal for its group is accepted
    Then the booking status is "CONFIRMED"

  Scenario: Reject confirmation for an unknown booking group
    Given no bookings exist for group "99999999-9999-9999-9999-999999999999"
    When the proposal for its group is accepted
    Then no booking is changed

  Scenario: Confirmation is idempotent at the booking state boundary
    Given a confirmed booking exists for passenger "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    When the proposal for its group is accepted again
    Then the booking status remains "CONFIRMED"
