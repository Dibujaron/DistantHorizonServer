import com.dibujaron.distanthorizon.database.persistence.StationKey
import org.jetbrains.exposed.dao.id.EntityID

class StationKeyInternal(
    val id: EntityID<Int>
) : StationKey()
{
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StationKeyInternal

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}