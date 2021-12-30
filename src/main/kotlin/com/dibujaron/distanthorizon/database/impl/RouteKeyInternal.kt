import com.dibujaron.distanthorizon.database.script.RouteKey
import org.jetbrains.exposed.dao.id.EntityID

class RouteKeyInternal(
    val id: EntityID<Int>
) : RouteKey() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RouteKeyInternal

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}