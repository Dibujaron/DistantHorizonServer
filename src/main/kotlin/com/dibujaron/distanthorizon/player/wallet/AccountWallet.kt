package com.dibujaron.distanthorizon.player.wallet

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.database.persistence.ActorInfo

class AccountWallet(private val myActor: ActorInfo) : Wallet
{
    var myBalance = myActor.balance
    override fun getBalance(): Int {
        return myBalance
    }

    override fun setBalance(newBal: Int) {
        myBalance = newBal
        BackgroundTaskManager.executeInBackground {
            DHServer.getDatabase().getPersistenceDatabase().updateActorBalance(myActor, newBal)
        }
    }

}