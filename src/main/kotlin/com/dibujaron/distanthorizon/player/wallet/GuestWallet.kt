package com.dibujaron.distanthorizon.player.wallet

import com.dibujaron.distanthorizon.DHServer

class GuestWallet : Wallet
{
    var bal = DHServer.startingBalance

    override fun getBalance(): Int {
        return bal
    }

    override fun setBalance(newBal: Int) {
        bal = newBal
    }
}