[
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld", // includes definitions for the core object (content, agent, created_at)
            "http://medici.ncsa.illinois.edu/prov.jsonld", // includes definitions for agent types
            {
                "extractor_id": {
                    "@id": "http://dts.ncsa.illinois.edu/api/extractor/id",
                    "@type": "@id" // string associated with extractor_id is a IRI
                },
                "score": "http://www.vision.caltech.edu/Image_Datasets/Caltech101/score",
                "category": "http://www.vision.caltech.edu/Image_Datasets/Caltech101/category"
            }],
        "created_at": "Fri Jan 16 15:57:20 CST 2015",
        "agent": {
            "@type": "cat:extractor", // cat definited in prov.jsonld
            "extractor_id": "http://dts.ncsa.illinois.edu/api/extractors/ncsa.cv.caltech101" // definited in local context
        },
        "content": {
            "score": [
                "-0.275160"
            ],
            "category": [
                "wrench"
            ]
        }
    },
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld", // could the standard definitions be move to the root?
            "http://medici.ncsa.illinois.edu/prov.jsonld",
            "http://dts.ncsa.illinois.edu/api/extractors/ncsa.cv.caltech101/metadata.jsonld" // extractor's defined context
        ],
        "created_at": "Fri Jan 16 15:57:20 CST 2015",
        "agent": {
            "@type": "cat:extractor",
            "extractor_id": "http://dts.ncsa.illinois.edu/api/extractors/ncsa.cv.caltech101"
        },
        "content": {
            "score": [
                "-0.275160"
            ],
            "category": [
                "wrench"
            ]
        }
    },
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld",
            "http://medici.ncsa.illinois.edu/prov.jsonld",
            {
                "dc": "http://purl.org/dc/terms/" // namespace syntax
            }],
        "created_at": "Fri Jan 16 9:51:20 CST 2015",
        "agent": {
            "@type": "cat:user",
            "user_id": "http://dts.ncsa.illinois.edu/api/users/52f1749ad6c40e37d0fe2ee7"
        },
        "content": {
            "dc:abstract": [
                "This is the abstract"
            ],
            "dc:alternative": [
                "This is the alternate title"
            ]
        }
    },
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld",
            "http://medici.ncsa.illinois.edu/prov.jsonld",
            {
                "abstract": "http://purl.org/dc/terms/abstract",
                "alternative": "http://purl.org/dc/terms/alternative"
            }],
        "created_at": "Fri Jan 16 9:51:20 CST 1971",
        "agent": {
            "@type": "http://dbpedia.org/ontology/MusicalArtist",
            "user_id": "http://dbpedia.org/resource/John_Lennon"
        },
        "source": "http://dbpedia.org/resource/random_fact",
        "content": {
            "abstract": "Imagine there's no countries"
        }
    }
]