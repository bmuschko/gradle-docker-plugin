// tag::create-task[]
plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

// Import task types
import com.bmuschko.gradle.docker.tasks.image.*

// Use task types
tasks.create("buildMyAppImage", DockerBuildImage::class) {
    inputDir.set(file("docker/myapp"))
    images.add("test/myapp:latest")
}
// end::create-task[]

// tag::repositories[]
repositories {
    mavenCentral()
}
// end::repositories[]

// tag::extension-tls[]
docker {
    url.set("https://192.168.59.103:2376")
    certPath.set(File(System.getProperty("user.home"), ".boot2docker/certs/boot2docker-vm"))

    registryCredentials {
        url.set("https://index.docker.io/v1/")
        username.set("bmuschko")
        password.set("pwd")
        email.set("benjamin.muschko@gmail.com")
    }
}
// end::extension-tls[]

// tag::extension-google-cloud[]
docker {
    registryCredentials {
        url.set("https://gcr.io")
        username.set("_json_key")
        password.set(file("keyfile.json").readText())
    }
}
// end::extension-google-cloud[]

// tag::extension-without-tls[]
docker {
    url.set("tcp://192.168.59.103:2375")
}
// end::extension-without-tls[]