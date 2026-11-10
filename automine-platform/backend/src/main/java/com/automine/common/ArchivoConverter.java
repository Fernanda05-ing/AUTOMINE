package com.automine.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Al guardar un archivo, si Supabase Storage esta configurado lo sube alli y en la base deja
 * solo la referencia. Al leer devuelve el valor tal cual (la referencia), para no descargar
 * archivos al listar registros: el contenido se obtiene con {@link ArchivoStore#contenido}.
 */
@Converter
public class ArchivoConverter implements AttributeConverter<byte[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(byte[] valor) {
        if (valor == null || ArchivoStore.esReferencia(valor)) {
            return valor;
        }
        ArchivoStore s = ArchivoStore.instancia();
        return s != null && s.remoto() ? s.subir(valor) : valor;
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] valor) {
        return valor;
    }
}
