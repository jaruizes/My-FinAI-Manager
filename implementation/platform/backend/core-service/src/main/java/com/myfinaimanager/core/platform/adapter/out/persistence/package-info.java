/**
 * Outbound persistence adapters (convention placeholder — no classes in EN001).
 *
 * <p>PostgreSQL implementations of outbound ports. Owns the mapping between provider/persistence
 * structures and the canonical domain model. Each capability owns its own schema; no cross-module
 * persistence access. Populated by FD001 onward.
 */
package com.myfinaimanager.core.platform.adapter.out.persistence;
