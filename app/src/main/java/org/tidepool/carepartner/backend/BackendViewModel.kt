package org.tidepool.carepartner.backend

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tidepool.tidepoolsdkjava.config.Environment
import java.util.EnumSet
import java.util.stream.Collectors.groupingBy
import java.util.stream.Collectors.toCollection
import java.util.stream.Stream

class BackendViewModel : ViewModel() {
    private val _realm: MutableLiveData<Realm> = MutableLiveData(Realm.Prod)
    val realm: LiveData<Realm> = _realm

    fun setEnv(env: Realm? = null) {
        _realm.value = env ?: Realm.Prod;
    }

    fun getAuthUri(): Uri {
       return _realm.value?.getUri() ?: Realm.defaultUri()
    }

    fun getAuthEnv(): Environment {
        return _realm.value?.getEnv() ?: Realm.defaultEnv()
    }

    companion object {
        private val envToRealms: Map<Environment, Set<Realm>> =
            Stream.of(*Realm.entries.toTypedArray())
            .collect(groupingBy(Realm::getEnv, toCollection(BackendViewModel::emptySet)))

        private fun emptySet(): EnumSet<Realm> {
            return EnumSet.noneOf(Realm::class.java)
        }

        fun getRealms(env: Environment) : Set<Realm> {
            return envToRealms[env] ?: emptySet();
        }
    }

    private enum class AuthServer(val env: Environment, server: String) {
        Prod(Environment.prod, "https://auth.tidepool.org"),
        Int(Environment.int_, "https://auth.external.tidepool.org"),
        Qa(Environment.qa2, "https://auth.qa2.tidepool.org"),
        Dev(Environment.dev, "https://auth.dev.tidepool.org");
        val serverUri: Uri = Uri.parse(server)
    }

    enum class Realm(private val realmName: String, private val authServer: AuthServer) {
        Dev("dev1", AuthServer.Dev),
        Qa1("qa1", AuthServer.Qa),
        Qa2("qa2", AuthServer.Qa),
        Qa3("qa3", AuthServer.Qa),
        Qa4("qa4", AuthServer.Qa),
        Qa5("qa5", AuthServer.Qa),
        Integration("integration", AuthServer.Int),
        Prod("tidepool", AuthServer.Prod);

        fun getEnv(): Environment {
            return authServer.env;
        }

        fun getUri(): Uri {
            return authServer.serverUri;
        }

        fun getRealmName(): String {
            return realmName
        }

        companion object {
            fun defaultEnv(): Environment {
                return Prod.getEnv();
            }

            fun defaultUri(): Uri {
                return Prod.getUri()
            }
        }
    }
}