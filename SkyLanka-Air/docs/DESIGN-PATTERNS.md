# Design patterns in SkyLanka Air

Four GoF patterns are used where they solve a real problem in the booking and payment flow. This file says where each one is, why it was chosen and what it improves, so it can be explained and defended at the viva.

| Pattern | Role | Files |
|---|---|---|
| **Observer** | Booking and payment events notify independent observers | `shared/event/` (`DomainEvent`, `DomainEventListener`, `DomainEventPublisher`, `InAppNotificationObserver`, `EmailNotificationObserver`, `EventLogObserver`) |
| **Strategy** | Interchangeable payment gateways behind one interface | `payment/service/PaymentGateway` (interface), `MockPaymentGateway` (cards), `BankTransferGateway` |
| **Factory** | Picks the right gateway for the chosen payment method | `payment/service/PaymentGatewayFactory` |
| **Decorator** | Adds payment-attempt auditing around any gateway | `payment/service/AuditingPaymentGateway` |

## Observer

**Problem.** `BookingService` and `PaymentService` used to build the in-app `Notification` and call the email service themselves, in about a dozen places. Adding SMS or push, or changing a message, meant editing booking and payment logic.

**Solution.** Services call `events.publish(new DomainEvent(type, booking, recipient, title, message))`. `DomainEventPublisher` (the subject) calls every `DomainEventListener` bean (the observers) in `@Order` sequence:

1. `InAppNotificationObserver` saves the `Notification` row (the bell icon and notifications page).
2. `EmailNotificationObserver` sends the email for `BOOKING_CREATED` and `PAYMENT_COMPLETED`.
3. `EventLogObserver` writes a trace line.

**Where it is used.** `BookingService.announce(...)` (create, modify, cancel, expire, flight delay) and `PaymentService.announce(...)` (completed, declined, verified, refunded, voided).

**Benefits.**
- Open for extension: a new reaction is one new `@Component` class, with no change to the services.
- An observer that fails (for example the mail server is down) is logged and never breaks the booking or payment.
- Observers run in the caller's transaction, so notifications are still saved or rolled back with the booking.

## Strategy, Factory and Decorator (payments)

```
PaymentService.pay(...)
      |  gateways.forMethod(method)            <- Factory
      v
AuditingPaymentGateway                         <- Decorator (same interface, adds audit log)
      |  delegates to
      v
PaymentGateway (interface)                     <- Strategy
   |-- MockPaymentGateway     (VISA, MASTERCARD: MOCK-TX ids, sandbox decline card)
   |-- BankTransferGateway    (BANK_TRANSFER: BANK-TX ids)
```

- **Strategy.** `PaymentService` depends only on the `PaymentGateway` interface. The two gateways differ in how they authorise and in the transaction reference they issue. A real provider is a third implementation.
- **Factory.** `PaymentGatewayFactory.forMethod(PaymentMethod)` returns the correct strategy, so `PaymentService` contains no `if (bank transfer)` logic.
- **Decorator.** The factory wraps each gateway in `AuditingPaymentGateway`, which logs method, booking reference, outcome and transaction id (never card numbers) to the `payment-audit` logger and returns the inner result unchanged. Auditing is added without touching either gateway.

**Benefits.** New payment methods and cross-cutting behaviour (auditing, and later timing or retries) are added without editing `PaymentService`.

## Singleton (not hand-written)

Spring creates one instance of every `@Service` and `@Component`, including `DomainEventPublisher` and `PaymentGatewayFactory`. That is the container's singleton scope. It is not listed above as one of the project's patterns because the code does not implement it by hand.

## Relation to the design document

These are internal refactors. They do not change the use-case diagram, the activity flows or the class diagram's classes:

- `Notification.send()` (class diagram) is now carried out by the observers.
- `Payment.processPayment(amount)` is still `PaymentService.pay(...)`: approve sets the payment to PAID and issues the ticket; a decline records a FAILED attempt and allows a retry.
- Customers still receive notifications for booking, payment, cancellation, expiry and flight-delay events, and email still follows `notifications.email.enabled`.

## Tests

- `DomainEventPublisherTest`: observers run in order, a failing observer does not stop the others, in-app observer stores the notification and ignores events with no recipient.
- `PaymentGatewayFactoryTest`: each method maps to the right gateway, the sandbox decline card still declines through the decorator, and the decorator returns the wrapped result unchanged.
- `BookingServiceTest` and `PaymentServiceTest` run against the new wiring.
