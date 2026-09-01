/**
 * Composition root / framework wiring (EN001: reserved, no classes yet).
 *
 * <p>The only place Spring configuration, {@code @Configuration} classes and bean wiring may live.
 * Keeping wiring here lets {@code platform.*.domain} and {@code platform.*.application} stay
 * framework-free. Populated when FD001 needs to bind ports to adapters.
 */
package com.myfinaimanager.core.bootstrap;
