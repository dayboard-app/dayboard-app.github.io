package io.github.dayboard.data

/**
 * A fresh identifier, made here rather than by the database.
 *
 * Everything the user creates has to appear the instant they create it, before any
 * write has left the device, and a row on screen needs an id to be ticked, edited
 * or dragged. Waiting for the server to name it would mean a row that exists but
 * cannot be touched for as long as the network takes.
 *
 * `crypto.randomUUID` is available in every secure context, which includes
 * `localhost` and the deployed site, and nowhere this app can be served from.
 */
internal fun newId(): String = js("crypto.randomUUID()") as String
