import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class ExpenseListItem {
    data class Header(
        val date: String,
        val total: String,
        val isPositive: Boolean
    ) : ExpenseListItem()

    @Parcelize
    data class Item(
        val iconResId: Int,
        val name: String,
        val budget: String,
        val amount: String,
        val date: String,
        val type: String
    ) : ExpenseListItem(), Parcelable
}
