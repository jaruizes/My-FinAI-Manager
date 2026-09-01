/**
 * Inbound web adapters (convention placeholder — no classes in EN001).
 *
 * <p>REST controllers that implement the OpenAPI contract under
 * {@code implementation/platform/contracts/openapi/} and delegate to inbound ports. MUST NOT
 * depend on outbound adapters. Populated by FD001 onward. (Operational health is served by Spring
 * Boot Actuator, not by a controller here.)
 */
package com.myfinaimanager.core.platform.adapter.in.web;
