package com.myselavu.app.ui.components

import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money

/** "₹1,23,456" or "₹••••" when amounts are hidden. */
fun Money.display(hidden: Boolean = false): String = if (hidden) IndianFormat.hidden() else IndianFormat.format(this)
