package com.dibujaron.distanthorizon.player.wallet

interface Wallet {
    fun getBalance(): Int
    fun setBalance(newBal: Int)
}