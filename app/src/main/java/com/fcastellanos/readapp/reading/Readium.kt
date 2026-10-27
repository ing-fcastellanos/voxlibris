package com.fcastellanos.readapp.reading

import android.content.Context
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Punto único de configuración del toolkit Readium: abre archivos y los convierte en [org.readium.r2.shared.publication.Publication]. */
class Readium(context: Context) {

    val httpClient: DefaultHttpClient = DefaultHttpClient()

    val assetRetriever: AssetRetriever = AssetRetriever(context.contentResolver, httpClient)

    val publicationOpener: PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )
}
