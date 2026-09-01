/**
 * Hexagonal Architecture package convention for {@code core-service} (EN001).
 *
 * <p>This namespace is a <strong>documented convention with no production classes yet</strong>.
 * Feature Definitions (starting with FD001) add real capability modules as siblings of this
 * package (for example {@code com.myfinaimanager.core.portfolio}), each using the same internal
 * layout:
 *
 * <pre>
 *   &lt;capability&gt;
 *   ├── domain                     business concepts, rules, deterministic calculations, invariants
 *   ├── application
 *   │   ├── port.in                inbound ports (use cases the capability exposes)
 *   │   └── port.out               outbound ports (dependencies the capability requires)
 *   └── adapter
 *       ├── in.web                 REST controllers implementing the OpenAPI contract
 *       └── out.persistence        PostgreSQL / other outbound adapters
 * </pre>
 *
 * <p>Dependency direction points inward: {@code adapter -> application -> domain}. The
 * {@code domain} and {@code application} layers must not depend on frameworks or infrastructure
 * (Spring, JDBC, HTTP clients, serialization). All Spring wiring lives in
 * {@link com.myfinaimanager.core.bootstrap}. The rules are enforced by
 * {@code com.myfinaimanager.core.architecture.HexagonalArchitectureRulesTest}.
 */
package com.myfinaimanager.core.platform;
