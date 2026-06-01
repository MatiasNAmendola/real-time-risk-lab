@cqrs @event-sourcing @atdd
Feature: Cuenta bancaria simple con CQRS y Event Sourcing
  Como arquitecto de una plataforma de riesgo
  Quiero un ejemplo simple de cuenta bancaria basado en eventos
  Para explicar CQRS, Event Sourcing, rehidratación y proyecciones sin mezclar Saga ni ledger avanzado

  Background:
    Given el servicio de transacciones distribuidas está corriendo

  @smoke
  Scenario: abrir una cuenta y depositar dinero
    When abro una cuenta por HTTP
    And deposito dinero en la cuenta
    Then la consulta de balance devuelve el estado rehidratado desde eventos
    And la proyección de balance coincide con el estado rehidratado

  @regression
  Scenario: auditar eventos de la cuenta
    When abro una cuenta por HTTP
    And deposito dinero dos veces
    Then el Event Store expone AccountOpened y MoneyDeposited para esa cuenta

  @advanced
  Scenario: ejecutar una Saga avanzada con compensación
    When publico una Saga con escenario FAIL_AFTER_LEDGER
    Then la Saga finaliza con estado COMPENSATED
    And existe una transferencia compensatoria
