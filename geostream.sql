--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner:
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner:
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner:
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner:
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: datapoints; Type: TABLE; Schema: public; Owner: clowder
--

CREATE TABLE datapoints (
    gid integer NOT NULL,
    geog geography,
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    data json,
    stream_id integer,
    created timestamp with time zone
);


ALTER TABLE datapoints OWNER TO clowder;

--
-- Name: geoindex_gid_seq; Type: SEQUENCE; Schema: public; Owner: clowder
--

CREATE SEQUENCE geoindex_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE geoindex_gid_seq OWNER TO clowder;

--
-- Name: geoindex_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: clowder
--

ALTER SEQUENCE geoindex_gid_seq OWNED BY datapoints.gid;


--
-- Name: sensors; Type: TABLE; Schema: public; Owner: clowder
--

CREATE TABLE sensors (
    gid integer NOT NULL,
    name character varying(255),
    geog geography,
    created timestamp with time zone,
    metadata json
);


ALTER TABLE sensors OWNER TO clowder;

--
-- Name: sensors_gid_seq; Type: SEQUENCE; Schema: public; Owner: clowder
--

CREATE SEQUENCE sensors_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE sensors_gid_seq OWNER TO clowder;

--
-- Name: sensors_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: clowder
--

ALTER SEQUENCE sensors_gid_seq OWNED BY sensors.gid;


--
-- Name: streams; Type: TABLE; Schema: public; Owner: clowder
--

CREATE TABLE streams (
    gid integer NOT NULL,
    name character varying(255),
    geog geography,
    created timestamp with time zone,
    metadata json,
    sensor_id integer,
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    params text[]
);


ALTER TABLE streams OWNER TO clowder;

--
-- Name: streams_gid_seq; Type: SEQUENCE; Schema: public; Owner: clowder
--

CREATE SEQUENCE streams_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE streams_gid_seq OWNER TO clowder;

--
-- Name: streams_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: clowder
--

ALTER SEQUENCE streams_gid_seq OWNED BY streams.gid;


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY datapoints ALTER COLUMN gid SET DEFAULT nextval('geoindex_gid_seq'::regclass);


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY sensors ALTER COLUMN gid SET DEFAULT nextval('sensors_gid_seq'::regclass);


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY streams ALTER COLUMN gid SET DEFAULT nextval('streams_gid_seq'::regclass);


--
-- Name: geoindex_pkey; Type: CONSTRAINT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY datapoints
ADD CONSTRAINT geoindex_pkey PRIMARY KEY (gid);


--
-- Name: sensors_pkey; Type: CONSTRAINT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY sensors
ADD CONSTRAINT sensors_pkey PRIMARY KEY (gid);


--
-- Name: streams_pkey; Type: CONSTRAINT; Schema: public; Owner: clowder
--

ALTER TABLE ONLY streams
ADD CONSTRAINT streams_pkey PRIMARY KEY (gid);


--
-- Name: geoindex_gix; Type: INDEX; Schema: public; Owner: clowder
--

CREATE INDEX geoindex_gix ON datapoints USING gist (geog);


--
-- Name: geoindex_stream_id; Type: INDEX; Schema: public; Owner: clowder
--

CREATE INDEX geoindex_stream_id ON datapoints USING btree (stream_id);


--
-- Name: geoindex_times; Type: INDEX; Schema: public; Owner: clowder
--

CREATE INDEX geoindex_times ON datapoints USING btree (start_time, end_time);


--
-- Name: sensors_gix; Type: INDEX; Schema: public; Owner: clowder
--

CREATE INDEX sensors_gix ON sensors USING gist (geog);


--
-- Name: streams_gix; Type: INDEX; Schema: public; Owner: clowder
--

CREATE INDEX streams_gix ON streams USING gist (geog);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO clowder;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- Name: datapoints; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON TABLE datapoints FROM PUBLIC;
REVOKE ALL ON TABLE datapoints FROM clowder;
GRANT ALL ON TABLE datapoints TO clowder;


--
-- Name: geoindex_gid_seq; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON SEQUENCE geoindex_gid_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE geoindex_gid_seq FROM clowder;
GRANT ALL ON SEQUENCE geoindex_gid_seq TO clowder;


--
-- Name: sensors; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON TABLE sensors FROM PUBLIC;
REVOKE ALL ON TABLE sensors FROM clowder;
GRANT ALL ON TABLE sensors TO clowder;


--
-- Name: sensors_gid_seq; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON SEQUENCE sensors_gid_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE sensors_gid_seq FROM clowder;
GRANT ALL ON SEQUENCE sensors_gid_seq TO clowder;


--
-- Name: streams; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON TABLE streams FROM PUBLIC;
REVOKE ALL ON TABLE streams FROM clowder;
GRANT ALL ON TABLE streams TO clowder;


--
-- Name: streams_gid_seq; Type: ACL; Schema: public; Owner: clowder
--

REVOKE ALL ON SEQUENCE streams_gid_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE streams_gid_seq FROM clowder;
GRANT ALL ON SEQUENCE streams_gid_seq TO clowder;


--
-- PostgreSQL database dump complete
--

