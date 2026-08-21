package de.optadata.odil.learnwithme.identity

import java.util.UUID

/** Öffentlicher Port des `identity`-Moduls für andere Module (Spring-Modulith Named Interface). */
interface IdentityApi {
    /** Plan des Workspace-Inhabers ("FREE" | "PRO" | "EDU") — u.a. für die Quota-Prüfung im `ai`-Modul (A6). */
    fun getPlan(workspaceId: UUID): String
}
