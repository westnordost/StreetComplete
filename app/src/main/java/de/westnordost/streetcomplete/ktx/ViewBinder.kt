package de.westnordost.streetcomplete.ktx

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Created by sumanabhi
 * on 20,May,2021
 * at 15:49
 **/

inline fun <reified T : ViewBinding> AppCompatActivity.viewBinding(noinline viewInflater: (LayoutInflater) -> T) =
    ActivityBindingPropertyDelegate(this, viewInflater)

class ActivityBindingPropertyDelegate<T : ViewBinding>(
    private val activity: AppCompatActivity,
    private val viewInflater: (LayoutInflater) -> T
) : ReadOnlyProperty<AppCompatActivity, T>, LifecycleEventObserver {

    private var binding: T? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun getValue(thisRef: AppCompatActivity, property: KProperty<*>): T {
        return getBinding()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Event) {
        if (event == Event.ON_CREATE) {
            activity.setContentView(getBinding().root)
        } else if (event == Event.ON_DESTROY) {
            binding = null
            source.lifecycle.removeObserver(this)
        }
    }

    private fun getBinding(): T {
        return binding ?: viewInflater.invoke(activity.layoutInflater).also { binding = it }
    }

}

inline fun <reified T : ViewBinding> Fragment.viewBinding(
    noinline viewBinder: (View) -> T,
    noinline destroyer: ((T) -> Unit)? = null
) = FragmentViewBindingPropertyDelegate(this, viewBinder, destroyer)

class FragmentViewBindingPropertyDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val viewBinder: (View) -> T,
    private var destroyer: ((T) -> Unit)?
) : ReadOnlyProperty<Fragment, T>, LifecycleEventObserver {

    private var binding: T? = null

    override fun onStateChanged(source: LifecycleOwner, event: Event) {
        if (event == Event.ON_DESTROY) {
            destroyer?.invoke(binding!!)
            destroyer = null
            binding = null
            source.lifecycle.removeObserver(this)
        }
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return binding ?: viewBinder(thisRef.requireView()).also {
            binding = it
            fragment.viewLifecycleOwner.lifecycle.addObserver(this)
        }
    }

}

/**
 * you can use this in your activity like this
 * in case of an @Activity
 * private val binding by viewBinding(<your_activity_layout_binding>::inflate)
 * and in case of a @Fragment
 * private val binding by viewBinding(<your_fragment_layout_binding>::bind())
 */
