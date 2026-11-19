package com.reeware.android.breed.engine

/** Progression status of a room node on a spaceship sector map. */
enum class RoomStatus(private val labelDe: String, private val labelEn: String) {
    LOCKED("Gesperrt", "Locked"),
    AVAILABLE("Bereit zum Angriff", "Ready to Attack"),
    CONQUERED("Erobert", "Conquered"),
    LOST("Verloren – Zurückerobern!", "Lost – Reclaim!");

    val label: String get() = GameLocale.tr(labelDe, labelEn)
}

/** Optional hive infrastructure built in a conquered spaceship room. */
enum class RoomUpgrade(
    private val displayNameDe: String,
    private val displayNameEn: String,
    private val bonusHintDe: String,
    private val bonusHintEn: String,
    val costMutagens: Int
) {
    NONE("Unbebaut", "Unbuilt", "Kein Ausbau aktiv", "No upgrade active", 0),
    BROOD_CHAMBER("Brutkammer", "Brood Chamber", "+1 Start-Biomasse im Gefecht", "+1 starting Biomass in battle", 25),
    BIO_EXTRACTOR("Biomasse-Extraktor", "Bio Extractor", "+10 Mutagene pro Sieg", "+10 Mutagens per victory", 25),
    GENE_INCUBATOR("Gen-Inkubator", "Gene Incubator", "+1 Max-Befehl in Phase 1", "+1 max command in Phase 1", 30);

    val displayName: String get() = GameLocale.tr(displayNameDe, displayNameEn)
    val bonusHint: String get() = GameLocale.tr(bonusHintDe, bonusHintEn)
}

/** Graph node describing one spaceship room inside a planetary chapter. */
data class ShipRoomNode(
    val levelId: String,
    val title: String,
    val shipName: String,
    val planetTheme: PlanetTheme,
    val mapX: Float,
    val mapY: Float,
    val connectedIds: List<String>,
    val mutagensReward: Int,
    val initiallyAvailable: Boolean = false
)

/** Result of applying a defeat penalty when humans survive or escape. */
data class DefeatPenalty(
    val lostRoomId: String?,
    val lostRoomTitle: String?
)

/** Persistent player meta-progression across spaceship rooms and fixed mutation trees. */
data class CampaignProgress(
    val mutagens: Int,
    val roomStatuses: Map<String, RoomStatus>,
    val roomUpgrades: Map<String, RoomUpgrade>,
    val unlockedMutations: Set<MutationNode>,
    val alphaUnlockedSpecies: Set<AlienSpecies>,
    val conquestHistory: List<String>
) {
    fun extraBiomassBonus(): Int =
        roomStatuses.count { (id, status) ->
            status == RoomStatus.CONQUERED && roomUpgrades[id] == RoomUpgrade.BROOD_CHAMBER
        }

    fun extraCommandBonus(): Int =
        roomStatuses.count { (id, status) ->
            status == RoomStatus.CONQUERED && roomUpgrades[id] == RoomUpgrade.GENE_INCUBATOR
        }

    fun victoryBonusMutagens(): Int =
        roomStatuses.count { (id, status) ->
            status == RoomStatus.CONQUERED && roomUpgrades[id] == RoomUpgrade.BIO_EXTRACTOR
        } * 10

    fun isRoomPlayable(levelId: String): Boolean {
        val status = roomStatuses[levelId] ?: RoomStatus.LOCKED
        return status != RoomStatus.LOCKED
    }

    fun applyVictory(nodes: List<ShipRoomNode>, levelId: String): Pair<CampaignProgress, Int> {
        val node = nodes.firstOrNull { it.levelId == levelId }
        val newStatuses = roomStatuses.toMutableMap()
        val wasAlreadyConquered = newStatuses[levelId] == RoomStatus.CONQUERED
        newStatuses[levelId] = RoomStatus.CONQUERED

        if (node != null) {
            for (nextId in node.connectedIds) {
                if (newStatuses[nextId] == null || newStatuses[nextId] == RoomStatus.LOCKED) {
                    newStatuses[nextId] = RoomStatus.AVAILABLE
                }
            }
        }

        val reward = if (wasAlreadyConquered) {
            10 + victoryBonusMutagens()
        } else {
            (node?.mutagensReward ?: 20) + victoryBonusMutagens()
        }

        val newHistory = conquestHistory.filter { it != levelId } + levelId
        return copy(
            mutagens = mutagens + reward,
            roomStatuses = newStatuses,
            conquestHistory = newHistory
        ) to reward
    }

    /**
     * When humans survive or escape a room, the hive loses one previously conquered room.
     */
    fun applyDefeat(nodes: List<ShipRoomNode>): Pair<CampaignProgress, DefeatPenalty> {
        val victimId = conquestHistory.lastOrNull { roomStatuses[it] == RoomStatus.CONQUERED }
            ?: roomStatuses.entries.firstOrNull { it.value == RoomStatus.CONQUERED }?.key

        if (victimId == null) {
            return this to DefeatPenalty(null, null)
        }

        val newStatuses = roomStatuses.toMutableMap()
        newStatuses[victimId] = RoomStatus.LOST

        val newUpgrades = roomUpgrades.toMutableMap()
        newUpgrades.remove(victimId)

        val newHistory = conquestHistory.filter { it != victimId }
        val lostTitle = nodes.firstOrNull { it.levelId == victimId }?.title ?: victimId

        return copy(
            roomStatuses = newStatuses,
            roomUpgrades = newUpgrades,
            conquestHistory = newHistory
        ) to DefeatPenalty(victimId, lostTitle)
    }

    fun unlockMutation(node: MutationNode): CampaignProgress? {
        if (node in unlockedMutations || mutagens < node.costMutagens) return null
        return copy(
            mutagens = mutagens - node.costMutagens,
            unlockedMutations = unlockedMutations + node
        )
    }

    fun unlockAlpha(species: AlienSpecies, cost: Int = 40): CampaignProgress? {
        if (species in alphaUnlockedSpecies || mutagens < cost) return null
        return copy(
            mutagens = mutagens - cost,
            alphaUnlockedSpecies = alphaUnlockedSpecies + species
        )
    }

    fun upgradeRoom(levelId: String, upgrade: RoomUpgrade): CampaignProgress? {
        if (roomStatuses[levelId] != RoomStatus.CONQUERED) return null
        if (upgrade == RoomUpgrade.NONE || roomUpgrades[levelId] == upgrade) return null
        if (mutagens < upgrade.costMutagens) return null
        val newUpgrades = roomUpgrades.toMutableMap()
        newUpgrades[levelId] = upgrade
        return copy(
            mutagens = mutagens - upgrade.costMutagens,
            roomUpgrades = newUpgrades
        )
    }

    companion object {
        fun initial(nodes: List<ShipRoomNode>): CampaignProgress {
            val statuses = nodes.associate { node ->
                node.levelId to if (node.initiallyAvailable) RoomStatus.AVAILABLE else RoomStatus.LOCKED
            }
            return CampaignProgress(
                mutagens = 25,
                roomStatuses = statuses,
                roomUpgrades = emptyMap(),
                unlockedMutations = emptySet(),
                alphaUnlockedSpecies = emptySet(),
                conquestHistory = emptyList()
            )
        }
    }
}
